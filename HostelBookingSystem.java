import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/*
 * College Hostel Booking System  -  NO DATABASE VERSION
 * Pure Java 17 + Swing. No MySQL, no jar files, no setup.
 *
 * - Data is saved automatically in a file called  hostel_data.dat
 *   (in the folder you run the program from). Delete that file to reset everything.
 * - Default admin login:  username = admin   password = admin123
 * - 20 demo rooms (10 boys, 10 girls) are created the first time you run it.
 */
public class HostelBookingSystem extends JFrame {

    // ---------- Theme ----------
    private static final Color PRIMARY  = new Color(37, 99, 235);
    private static final Color HEADER   = new Color(30, 41, 59);
    private static final Color BG       = new Color(241, 245, 249);
    private static final Color BORDER   = new Color(203, 213, 225);
    private static final Color MUTED    = new Color(100, 116, 139);
    private static final Color TEXT     = new Color(15, 23, 42);
    private static final Color ROW_ALT  = new Color(248, 250, 252);
    private static final Color SELECT   = new Color(219, 234, 254);
    private static final Color SUCCESS  = new Color(22, 163, 74);
    private static final Color DANGER   = new Color(220, 38, 38);
    private static final Color WARN     = new Color(217, 119, 6);
    private static final Color GRAY_BTN = new Color(100, 116, 139);

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final File FILE = new File("hostel_data.dat");

    // =====================================================================
    //  DATA MODEL (saved to file with Java serialization)
    // =====================================================================
    static class Student implements Serializable {
        private static final long serialVersionUID = 1L;
        int id, year;
        String regNo, name, gender, dept, phone, email, hash;
    }

    static class Room implements Serializable {
        private static final long serialVersionUID = 1L;
        int id, capacity, occupied;
        String hostel, no, status = "AVAILABLE";
        char type;                       // 'B' boys, 'G' girls
        BigDecimal fee;
    }

    static class Booking implements Serializable {
        private static final long serialVersionUID = 1L;
        int id, studentId, roomId;
        LocalDateTime date;
        String status;
    }

    static class Payment implements Serializable {
        private static final long serialVersionUID = 1L;
        int id, bookingId;
        BigDecimal amount;
        String mode, receipt;
        LocalDate paidOn;
    }

    static class Data implements Serializable {
        private static final long serialVersionUID = 1L;
        List<Student> students = new ArrayList<>();
        List<Room> rooms = new ArrayList<>();
        List<Booking> bookings = new ArrayList<>();
        List<Payment> payments = new ArrayList<>();
        Map<String, String> admins = new HashMap<>();   // username -> password hash
        int nextStudent = 1, nextRoom = 1, nextBooking = 1, nextPayment = 1;
    }

    private Data data;

    private void loadData() {
        if (FILE.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(FILE))) {
                data = (Data) in.readObject();
                return;
            } catch (Exception ex) {
                // unreadable file: start fresh below
            }
        }
        data = new Data();
        try { data.admins.put("admin", sha256("admin123")); } catch (Exception ignored) {}
        String[][] hostels = {{"Boys Hostel", "B"}, {"Girls Hostel", "G"}};
        for (String[] h : hostels) {
            for (int i = 1; i <= 10; i++) {
                Room r = new Room();
                r.id = data.nextRoom++;
                r.hostel = h[0];
                r.type = h[1].charAt(0);
                r.no = h[1] + (100 + i);
                r.capacity = (i % 2 == 0) ? 2 : 3;
                r.fee = new BigDecimal(r.capacity == 3 ? 45000 : 42000);
                data.rooms.add(r);
            }
        }
        save();
    }

    private void save() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(FILE))) {
            out.writeObject(data);
        } catch (IOException ex) {
            error("Could not save data: " + ex.getMessage());
        }
    }

    private Student studentById(int id) {
        for (Student s : data.students) if (s.id == id) return s;
        return null;
    }
    private Room roomById(int id) {
        for (Room r : data.rooms) if (r.id == id) return r;
        return null;
    }
    private Booking bookingById(int id) {
        for (Booking b : data.bookings) if (b.id == id) return b;
        return null;
    }
    private boolean isPaid(int bookingId) {
        for (Payment p : data.payments) if (p.bookingId == bookingId) return true;
        return false;
    }

    // ---------- State ----------
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private int studentId = -1;
    private String studentName = "", studentReg = "", studentGender = "";
    private JPanel studentPanel, adminPanel;
    private JTextField loginUser;
    private JPasswordField loginPass;

    public HostelBookingSystem() {
        super("College Hostel Booking System");
        loadData();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 680);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        root.add(buildLogin(), "login");
        root.add(buildRegister(), "register");
        setContentPane(root);
        show("login");
    }

    private void show(String name) { cards.show(root, name); }

    // =====================================================================
    //  LOGIN SCREEN
    // =====================================================================
    private JPanel buildLogin() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(BG);

        loginUser = field();
        loginPass = new JPasswordField();
        style(loginPass);
        JCheckBox showPw = new JCheckBox("Show password");
        showPw.setOpaque(false);
        showPw.setFont(font(Font.PLAIN, 12));
        showPw.setForeground(MUTED);
        showPw.setAlignmentX(Component.LEFT_ALIGNMENT);
        char echo = loginPass.getEchoChar();
        showPw.addActionListener(e -> loginPass.setEchoChar(showPw.isSelected() ? (char) 0 : echo));

        FlatButton login = new FlatButton("Log in", PRIMARY);
        login.setAlignmentX(Component.LEFT_ALIGNMENT);
        login.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        ActionListener go = e -> doLogin(loginUser.getText().trim(), new String(loginPass.getPassword()));
        login.addActionListener(go);
        loginUser.addActionListener(go);
        loginPass.addActionListener(go);

        JButton register = link("New student? Create an account");
        register.addActionListener(e -> show("register"));

        JPanel card = card();
        card.add(title("Hostel Booking System"));
        card.add(subtitle("Sign in with your register number or admin username"));
        card.add(Box.createVerticalStrut(22));
        card.add(group("Register number / Admin username", loginUser));
        card.add(group("Password", loginPass));
        card.add(showPw);
        card.add(Box.createVerticalStrut(16));
        card.add(login);
        card.add(Box.createVerticalStrut(10));
        card.add(register);
        page.add(card);
        return page;
    }

    private void doLogin(String name, String password) {
        if (name.isEmpty() || password.isEmpty()) {
            warn("Please enter your username and password.");
            return;
        }
        try {
            String hash = sha256(password);
            for (Student s : data.students) {
                if (s.regNo.equals(name) && s.hash.equals(hash)) {
                    studentId = s.id;
                    studentName = s.name;
                    studentReg = s.regNo;
                    studentGender = s.gender;
                    loginPass.setText("");
                    openStudent();
                    return;
                }
            }
            if (hash.equals(data.admins.get(name))) {
                loginPass.setText("");
                openAdmin();
                return;
            }
            error("Invalid username or password.");
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    private void logout() {
        studentId = -1;
        loginUser.setText("");
        loginPass.setText("");
        show("login");
    }

    // =====================================================================
    //  REGISTRATION SCREEN
    // =====================================================================
    private JPanel buildRegister() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(BG);

        JTextField reg = field(), name = field(), phone = field(), email = field();
        JComboBox<String> gender = new JComboBox<>(new String[]{"Male", "Female"});
        JComboBox<String> dept = new JComboBox<>(new String[]{"CSE", "ECE", "EEE", "IT", "MECH", "CIVIL", "Other"});
        dept.setEditable(true);
        JComboBox<Integer> year = new JComboBox<>(new Integer[]{1, 2, 3, 4});
        gender.setFont(font(Font.PLAIN, 14));
        dept.setFont(font(Font.PLAIN, 14));
        year.setFont(font(Font.PLAIN, 14));
        JPasswordField pw = new JPasswordField(), pw2 = new JPasswordField();
        style(pw);
        style(pw2);

        FlatButton submit = new FlatButton("Register", PRIMARY);
        JButton back = link("Back to login");
        back.addActionListener(e -> show("login"));

        submit.addActionListener(e -> {
            String regText = reg.getText().trim();
            String emailText = email.getText().trim();
            String deptText = String.valueOf(dept.getSelectedItem()).trim();
            String p1 = new String(pw.getPassword());
            String err = null;
            if (regText.isEmpty()) err = "Register number is required.";
            else if (name.getText().isBlank()) err = "Full name is required.";
            else if (deptText.isEmpty()) err = "Department is required.";
            else if (!phone.getText().trim().matches("\\d{10}")) err = "Phone must contain exactly 10 digits.";
            else if (!emailText.isEmpty() && !emailText.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
                err = "Please enter a valid email address.";
            else if (p1.length() < 6) err = "Password must be at least 6 characters.";
            else if (!p1.equals(new String(pw2.getPassword()))) err = "Passwords do not match.";
            if (err != null) { warn(err); return; }

            for (Student s : data.students) {
                if (s.regNo.equalsIgnoreCase(regText)
                        || (!emailText.isEmpty() && emailText.equalsIgnoreCase(s.email))) {
                    error("This register number or email is already registered.");
                    return;
                }
            }
            try {
                Student s = new Student();
                s.id = data.nextStudent++;
                s.regNo = regText;
                s.name = name.getText().trim();
                s.gender = gender.getSelectedIndex() == 0 ? "M" : "F";
                s.dept = deptText;
                s.year = (Integer) year.getSelectedItem();
                s.phone = phone.getText().trim();
                s.email = emailText.isEmpty() ? null : emailText;
                s.hash = sha256(p1);
                data.students.add(s);
                save();
                info("Registration successful! Please log in.");
                reg.setText(""); name.setText(""); phone.setText(""); email.setText("");
                pw.setText(""); pw2.setText("");
                show("login");
            } catch (Exception ex) {
                error("Registration failed: " + ex.getMessage());
            }
        });

        JPanel card = card();
        card.add(title("Student Registration"));
        card.add(subtitle("Fill in your details to create an account"));
        card.add(Box.createVerticalStrut(20));
        card.add(row(group("Register number", reg), group("Full name", name)));
        card.add(row(group("Gender", gender), group("Year of study", year)));
        card.add(row(group("Department", dept), group("Phone (10 digits)", phone)));
        card.add(row(group("Email (optional)", email), filler()));
        card.add(row(group("Password (min 6 characters)", pw), group("Confirm password", pw2)));
        card.add(Box.createVerticalStrut(6));
        submit.setAlignmentX(Component.LEFT_ALIGNMENT);
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        card.add(submit);
        card.add(Box.createVerticalStrut(8));
        card.add(back);
        page.add(card);
        return page;
    }

    // =====================================================================
    //  STUDENT DASHBOARD
    // =====================================================================
    private void openStudent() {
        if (studentPanel != null) root.remove(studentPanel);
        studentPanel = buildStudent();
        root.add(studentPanel, "student");
        show("student");
    }

    private char hostelType() { return "F".equalsIgnoreCase(studentGender) ? 'G' : 'B'; }

    private List<Object[]> roomRows() {
        List<Object[]> rows = new ArrayList<>();
        for (Room r : data.rooms) {
            if ("AVAILABLE".equals(r.status) && r.occupied < r.capacity && r.type == hostelType())
                rows.add(new Object[]{r.id, r.hostel, r.no, r.capacity, r.capacity - r.occupied, r.fee});
        }
        return rows;
    }

    private List<Object[]> myBookingRows() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = data.bookings.size() - 1; i >= 0; i--) {
            Booking b = data.bookings.get(i);
            if (b.studentId != studentId) continue;
            Room r = roomById(b.roomId);
            rows.add(new Object[]{b.id, r.hostel, r.no, b.date.format(DTF), b.status});
        }
        return rows;
    }

    private JPanel buildStudent() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        page.add(header("Welcome, " + studentName, "Register no: " + studentReg, this::logout), BorderLayout.NORTH);

        DataTable rooms = new DataTable(
            new String[]{"Room ID", "Hostel", "Room", "Capacity", "Vacant", "Annual Fee"}, this::roomRows);
        DataTable bookings = new DataTable(
            new String[]{"Booking ID", "Hostel", "Room", "Date", "Status"}, this::myBookingRows);

        FlatButton book = new FlatButton("Book selected room", PRIMARY);
        book.addActionListener(e -> {
            Object id = rooms.value(0);
            if (id == null) { warn("Please select a room from the table first."); return; }
            if (confirm("Request a booking for " + rooms.value(1) + " - Room " + rooms.value(2) + "?")) {
                requestBooking(((Number) id).intValue());
                rooms.refresh();
                bookings.refresh();
            }
        });

        FlatButton cancel = new FlatButton("Cancel pending request", DANGER);
        cancel.addActionListener(e -> {
            Object id = bookings.value(0);
            if (id == null) { warn("Please select a booking first."); return; }
            if (!"PENDING".equals(bookings.value(4))) { warn("Only PENDING requests can be cancelled."); return; }
            if (confirm("Cancel booking request #" + id + "?")) {
                cancelBooking(((Number) id).intValue());
                bookings.refresh();
            }
        });

        JTabbedPane tabs = tabs();
        tabs.addTab("  Available Rooms  ", tabPage(
            "Select a room, then click \"Book selected room\". Only rooms for your hostel are shown.",
            rooms, refreshButton(rooms), book));
        tabs.addTab("  My Bookings  ", tabPage(
            "Track the status of your booking requests.",
            bookings, refreshButton(bookings), cancel));
        tabs.addChangeListener(e -> (tabs.getSelectedIndex() == 0 ? rooms : bookings).refresh());

        rooms.refresh();
        bookings.refresh();
        page.add(tabs, BorderLayout.CENTER);
        return page;
    }

    private void requestBooking(int roomId) {
        Room r = roomById(roomId);
        if (r == null || !"AVAILABLE".equals(r.status) || r.occupied >= r.capacity || r.type != hostelType()) {
            warn("This room is no longer available.");
            return;
        }
        for (Booking b : data.bookings) {
            if (b.studentId == studentId && ("PENDING".equals(b.status) || "APPROVED".equals(b.status))) {
                warn("You already have an active booking.");
                return;
            }
        }
        Booking nb = new Booking();
        nb.id = data.nextBooking++;
        nb.studentId = studentId;
        nb.roomId = roomId;
        nb.date = LocalDateTime.now();
        nb.status = "PENDING";
        data.bookings.add(nb);
        save();
        info("Booking request submitted. Status: PENDING.");
    }

    private void cancelBooking(int bookingId) {
        Booking b = bookingById(bookingId);
        if (b != null && b.studentId == studentId && "PENDING".equals(b.status)) {
            b.status = "CANCELLED";
            save();
            info("Booking request cancelled.");
        } else {
            warn("This request could not be cancelled.");
        }
    }

    // =====================================================================
    //  ADMIN DASHBOARD
    // =====================================================================
    private void openAdmin() {
        if (adminPanel != null) root.remove(adminPanel);
        adminPanel = buildAdmin();
        root.add(adminPanel, "admin");
        show("admin");
    }

    private List<Object[]> pendingRows() {
        List<Object[]> rows = new ArrayList<>();
        for (Booking b : data.bookings) {
            if (!"PENDING".equals(b.status)) continue;
            Student s = studentById(b.studentId);
            Room r = roomById(b.roomId);
            rows.add(new Object[]{b.id, s.regNo, s.name, s.dept, s.year, r.hostel, r.no, b.date.format(DTF)});
        }
        return rows;
    }

    private List<Object[]> unpaidRows() {
        List<Object[]> rows = new ArrayList<>();
        for (Booking b : data.bookings) {
            if (!"APPROVED".equals(b.status) || isPaid(b.id)) continue;
            Student s = studentById(b.studentId);
            Room r = roomById(b.roomId);
            rows.add(new Object[]{b.id, s.regNo, s.name, r.hostel, r.no, r.fee});
        }
        return rows;
    }

    private List<Object[]> occupancyRows() {
        List<Object[]> rows = new ArrayList<>();
        for (Room r : data.rooms)
            rows.add(new Object[]{r.hostel, r.no, r.capacity, r.occupied, r.capacity - r.occupied, r.status});
        return rows;
    }

    private List<Object[]> feeRows() {
        List<Object[]> rows = new ArrayList<>();
        BigDecimal grand = BigDecimal.ZERO;
        int grandCount = 0;
        for (String mode : new String[]{"Cash", "DD", "Online"}) {
            BigDecimal sum = BigDecimal.ZERO;
            int n = 0;
            for (Payment p : data.payments) {
                if (p.mode.equals(mode)) { sum = sum.add(p.amount); n++; }
            }
            if (n > 0) rows.add(new Object[]{mode, sum, n});
            grand = grand.add(sum);
            grandCount += n;
        }
        rows.add(new Object[]{"TOTAL", grand, grandCount});
        return rows;
    }

    private JPanel buildAdmin() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        page.add(header("Administrator Dashboard", "Manage requests, payments and reports", this::logout),
                 BorderLayout.NORTH);

        DataTable pending = new DataTable(new String[]{"Booking ID", "Reg No", "Student", "Department",
            "Year", "Hostel", "Room", "Requested On"}, this::pendingRows);
        DataTable pay = new DataTable(new String[]{"Booking ID", "Reg No", "Student", "Hostel", "Room", "Fee"},
            this::unpaidRows);
        DataTable occupancy = new DataTable(new String[]{"Hostel", "Room", "Capacity", "Occupied", "Vacant",
            "Status"}, this::occupancyRows);
        DataTable fees = new DataTable(new String[]{"Payment Mode", "Total Collected", "Payments"}, this::feeRows);

        DataTable[] all = {pending, pay, occupancy, fees};
        Runnable refreshAll = () -> { for (DataTable t : all) t.refresh(); };

        FlatButton approve = new FlatButton("Approve", SUCCESS);
        FlatButton reject = new FlatButton("Reject", DANGER);
        approve.addActionListener(e -> handleDecision(pending, true, refreshAll));
        reject.addActionListener(e -> handleDecision(pending, false, refreshAll));

        JTextField amount = field();
        amount.setColumns(8);
        JComboBox<String> mode = new JComboBox<>(new String[]{"Cash", "DD", "Online"});
        mode.setFont(font(Font.PLAIN, 14));
        FlatButton payBtn = new FlatButton("Record payment", PRIMARY);
        pay.table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && pay.value(5) != null) amount.setText(pay.value(5).toString());
        });
        payBtn.addActionListener(e -> {
            Object id = pay.value(0);
            if (id == null) { warn("Select an approved booking from the table first."); return; }
            if (recordPayment(((Number) id).intValue(), amount.getText().trim(), (String) mode.getSelectedItem())) {
                amount.setText("");
                refreshAll.run();
            }
        });

        FlatButton addRoom = new FlatButton("Add room", SUCCESS);
        addRoom.addActionListener(e -> { if (addRoomDialog()) refreshAll.run(); });

        JTabbedPane tabs = tabs();
        tabs.addTab("  Pending Requests  ", tabPage(
            "Select a request and approve or reject it.", pending, refreshButton(pending), reject, approve));
        tabs.addTab("  Record Payment  ", tabPage(
            "Select an approved booking. The fee is filled in automatically; change it if needed.",
            pay, refreshButton(pay), small("Amount (Rs.)"), amount, small("Mode"), mode, payBtn));
        tabs.addTab("  Rooms & Occupancy  ", tabPage(
            "Room-wise occupancy and vacancy. Use \"Add room\" to create new rooms.",
            occupancy, refreshButton(occupancy), addRoom));
        tabs.addTab("  Fee Collection  ", tabPage(
            "Total fees collected by payment mode.", fees, refreshButton(fees)));
        tabs.addChangeListener(e -> all[tabs.getSelectedIndex()].refresh());

        refreshAll.run();
        page.add(tabs, BorderLayout.CENTER);
        return page;
    }

    private void handleDecision(DataTable t, boolean approve, Runnable after) {
        Object id = t.value(0);
        if (id == null) { warn("Please select a request first."); return; }
        if (confirm((approve ? "Approve" : "Reject") + " booking #" + id + " for " + t.value(2) + "?")) {
            decideBooking(((Number) id).intValue(), approve);
            after.run();
        }
    }

    private void decideBooking(int bookingId, boolean approve) {
        Booking b = bookingById(bookingId);
        if (b == null || !"PENDING".equals(b.status)) {
            error("This request is no longer pending.");
            return;
        }
        if (approve) {
            Room r = roomById(b.roomId);
            if (r == null || !"AVAILABLE".equals(r.status) || r.occupied >= r.capacity) {
                error("Room is full or under maintenance.");
                return;
            }
            r.occupied++;
        }
        b.status = approve ? "APPROVED" : "REJECTED";
        save();
        info(approve ? "Booking approved." : "Booking rejected.");
    }

    private boolean recordPayment(int bookingId, String amountText, String mode) {
        BigDecimal amount;
        try { amount = new BigDecimal(amountText); }
        catch (NumberFormatException ex) { warn("Please enter a valid amount."); return false; }
        if (amount.signum() <= 0) { warn("Amount must be greater than zero."); return false; }

        Booking b = bookingById(bookingId);
        if (b == null || !"APPROVED".equals(b.status) || isPaid(bookingId)) {
            error("Invalid booking or payment already recorded.");
            return false;
        }
        Payment p = new Payment();
        p.id = data.nextPayment++;
        p.bookingId = bookingId;
        p.amount = amount;
        p.mode = mode;
        p.receipt = "RCPT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        p.paidOn = LocalDate.now();
        data.payments.add(p);
        save();

        Student s = studentById(b.studentId);
        showReceipt("COLLEGE HOSTEL - PAYMENT RECEIPT\n"
            + "--------------------------------\n"
            + "Receipt    : " + p.receipt + "\n"
            + "Booking ID : " + bookingId + "\n"
            + "Student    : " + s.name + " (" + s.regNo + ")\n"
            + "Amount     : Rs. " + amount.toPlainString() + "\n"
            + "Mode       : " + mode + "\n"
            + "Date       : " + p.paidOn + "\n");
        return true;
    }

    private boolean addRoomDialog() {
        JComboBox<String> hostel = new JComboBox<>(new String[]{"Boys Hostel", "Girls Hostel"});
        JTextField no = field(), cap = field(), fee = field();
        Object[] form = {"Hostel", hostel, "Room number", no, "Capacity (students)", cap, "Annual fee (Rs.)", fee};
        if (JOptionPane.showConfirmDialog(this, form, "Add Room",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return false;
        try {
            String roomNo = no.getText().trim();
            int capacity = Integer.parseInt(cap.getText().trim());
            BigDecimal amount = new BigDecimal(fee.getText().trim());
            if (roomNo.isEmpty()) { warn("Room number is required."); return false; }
            if (capacity < 1 || capacity > 10) { warn("Capacity must be between 1 and 10."); return false; }
            if (amount.signum() <= 0) { warn("Fee must be greater than zero."); return false; }
            String hostelName = (String) hostel.getSelectedItem();
            for (Room r : data.rooms) {
                if (r.hostel.equals(hostelName) && r.no.equalsIgnoreCase(roomNo)) {
                    warn("That room already exists in this hostel.");
                    return false;
                }
            }
            Room r = new Room();
            r.id = data.nextRoom++;
            r.hostel = hostelName;
            r.type = hostel.getSelectedIndex() == 0 ? 'B' : 'G';
            r.no = roomNo;
            r.capacity = capacity;
            r.fee = amount;
            data.rooms.add(r);
            save();
            info("Room added.");
            return true;
        } catch (NumberFormatException ex) {
            warn("Please enter valid numbers for capacity and fee.");
            return false;
        }
    }

    private void showReceipt(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        area.setBorder(new EmptyBorder(12, 16, 12, 16));
        Object[] opts = {"Print", "Close"};
        int c = JOptionPane.showOptionDialog(this, area, "Payment Receipt",
            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts, opts[1]);
        if (c == 0) {
            try { area.print(); }
            catch (Exception ex) { error("Could not print: " + ex.getMessage()); }
        }
    }

    // =====================================================================
    //  TABLE COMPONENT
    // =====================================================================
    /** A styled, read-only, sortable table that reloads its rows from a supplier. */
    private static class DataTable extends JPanel {
        final DefaultTableModel model = new DefaultTableModel() {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        final JTable table = new JTable(model);
        final JLabel count = new JLabel(" ");
        final String[] cols;
        final Supplier<List<Object[]>> rows;

        DataTable(String[] cols, Supplier<List<Object[]>> rows) {
            super(new BorderLayout(0, 6));
            this.cols = cols;
            this.rows = rows;
            setOpaque(false);

            table.setRowHeight(30);
            table.setFont(font(Font.PLAIN, 13));
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setDefaultRenderer(Object.class, new CellRenderer());

            JTableHeader h = table.getTableHeader();
            h.setReorderingAllowed(false);
            h.setPreferredSize(new Dimension(0, 36));
            h.setDefaultRenderer((t, v, sel, foc, r, c) -> {
                JLabel l = new JLabel(v == null ? "" : v.toString());
                l.setOpaque(true);
                l.setBackground(HEADER);
                l.setForeground(Color.WHITE);
                l.setFont(font(Font.BOLD, 12));
                l.setBorder(new EmptyBorder(0, 10, 0, 10));
                return l;
            });

            JScrollPane sp = new JScrollPane(table);
            sp.setBorder(new LineBorder(BORDER));
            sp.getViewport().setBackground(Color.WHITE);
            count.setFont(font(Font.PLAIN, 12));
            count.setForeground(MUTED);
            add(sp, BorderLayout.CENTER);
            add(count, BorderLayout.SOUTH);
        }

        void refresh() {
            List<Object[]> data = rows.get();
            model.setDataVector(data.toArray(new Object[0][]), cols);
            count.setText(model.getRowCount() + " record(s)");
        }

        /** Value in the selected row (null if nothing is selected). */
        Object value(int col) {
            int r = table.getSelectedRow();
            if (r < 0) return null;
            return model.getValueAt(table.convertRowIndexToModel(r), col);
        }
    }

    private static class CellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                       boolean focus, int r, int c) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, false, r, c);
            l.setBorder(new EmptyBorder(0, 10, 0, 10));
            l.setHorizontalAlignment(v instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
            l.setBackground(sel ? SELECT : (r % 2 == 0 ? Color.WHITE : ROW_ALT));
            l.setForeground(TEXT);
            l.setFont(font(Font.PLAIN, 13));
            if (v != null && "Status".equals(t.getColumnName(c))) {
                l.setForeground(switch (String.valueOf(v).toUpperCase()) {
                    case "APPROVED", "AVAILABLE" -> SUCCESS;
                    case "PENDING" -> WARN;
                    case "REJECTED", "CANCELLED", "MAINTENANCE" -> DANGER;
                    default -> TEXT;
                });
                l.setFont(font(Font.BOLD, 13));
            }
            return l;
        }
    }

    // =====================================================================
    //  UI BUILDING BLOCKS
    // =====================================================================
    private static Font font(int style, int size) { return new Font("SansSerif", style, size); }

    private JPanel header(String title, String sub, Runnable onLogout) {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(HEADER);
        h.setBorder(new EmptyBorder(14, 24, 14, 24));
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(font(Font.BOLD, 20));
        t.setForeground(Color.WHITE);
        JLabel s = new JLabel(sub);
        s.setFont(font(Font.PLAIN, 12));
        s.setForeground(new Color(148, 163, 184));
        left.add(t);
        left.add(s);
        FlatButton out = new FlatButton("Log out", new Color(71, 85, 105));
        out.addActionListener(e -> { if (confirm("Log out now?")) onLogout.run(); });
        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        right.add(out);
        h.add(left, BorderLayout.WEST);
        h.add(right, BorderLayout.EAST);
        return h;
    }

    private JTabbedPane tabs() {
        JTabbedPane tp = new JTabbedPane();
        tp.setFont(font(Font.BOLD, 13));
        tp.setBackground(BG);
        return tp;
    }

    private JPanel tabPage(String hint, DataTable table, JComponent... actions) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(16, 20, 16, 20));
        JLabel h = new JLabel(hint);
        h.setFont(font(Font.PLAIN, 13));
        h.setForeground(MUTED);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bar.setOpaque(false);
        for (JComponent a : actions) bar.add(a);
        p.add(h, BorderLayout.NORTH);
        p.add(table, BorderLayout.CENTER);
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }

    private FlatButton refreshButton(DataTable t) {
        FlatButton b = new FlatButton("Refresh", GRAY_BTN);
        b.addActionListener(e -> t.refresh());
        return b;
    }

    private JLabel small(String text) {
        JLabel l = new JLabel(text);
        l.setFont(font(Font.BOLD, 12));
        l.setForeground(MUTED);
        return l;
    }

    private JPanel card() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        p.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(28, 34, 28, 34)));
        return p;
    }

    private JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(font(Font.BOLD, 24));
        l.setForeground(TEXT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel subtitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(font(Font.PLAIN, 13));
        l.setForeground(MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** Label above an input control. */
    private JPanel group(String label, JComponent c) {
        JPanel g = new JPanel();
        g.setLayout(new BoxLayout(g, BoxLayout.Y_AXIS));
        g.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(font(Font.BOLD, 12));
        l.setForeground(MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setPreferredSize(new Dimension(c.getPreferredSize().width, 38));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        g.add(l);
        g.add(Box.createVerticalStrut(5));
        g.add(c);
        g.add(Box.createVerticalStrut(14));
        g.setAlignmentX(Component.LEFT_ALIGNMENT);
        return g;
    }

    private JPanel row(JComponent a, JComponent b) {
        JPanel r = new JPanel(new GridLayout(1, 2, 16, 0));
        r.setOpaque(false);
        r.add(a);
        r.add(b);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    private JPanel filler() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    private JTextField field() {
        JTextField f = new JTextField(22);
        style(f);
        return f;
    }

    private void style(JTextField f) {
        f.setFont(font(Font.PLAIN, 14));
        f.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(6, 10, 6, 10)));
    }

    private JButton link(String text) {
        JButton b = new JButton(text);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setForeground(PRIMARY);
        b.setFont(font(Font.BOLD, 13));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }

    /** Flat, rounded button that looks the same on every operating system. */
    private static class FlatButton extends JButton {
        private final Color base;
        private boolean hover;

        FlatButton(String text, Color base) {
            super(text);
            this.base = base;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(font(Font.BOLD, 13));
            setBorder(new EmptyBorder(9, 18, 9, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = !isEnabled() ? new Color(180, 186, 196)
                : getModel().isPressed() ? base.darker().darker()
                : hover ? base.darker() : base;
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------- dialogs ----------
    private void info(String s) { JOptionPane.showMessageDialog(this, s, "Information", JOptionPane.INFORMATION_MESSAGE); }
    private void warn(String s) { JOptionPane.showMessageDialog(this, s, "Please check", JOptionPane.WARNING_MESSAGE); }
    private void error(String s) { JOptionPane.showMessageDialog(this, s, "Error", JOptionPane.ERROR_MESSAGE); }
    private boolean confirm(String s) {
        return JOptionPane.showConfirmDialog(this, s, "Please confirm",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private static String sha256(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new HostelBookingSystem().setVisible(true));
    }
}