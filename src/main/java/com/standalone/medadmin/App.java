package com.standalone.medadmin;

import com.standalone.medadmin.db.Database;
import com.standalone.medadmin.model.Medicine;
import com.standalone.medadmin.model.StockMovement;
import com.standalone.medadmin.repository.MedicineRepository;
import com.standalone.medadmin.service.ReportService;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class App extends Application {

    // ── Backend ──────────────────────────────────────────────────────────────
    private final MedicineRepository repo = new MedicineRepository();
    private final ReportService reportService = new ReportService();
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── Live UI references ───────────────────────────────────────────────────
    private final TableView<Medicine>      medicineTable  = new TableView<>();
    private final TableView<StockMovement> movementTable  = new TableView<>();
    private final Label   statsLabel        = new Label();
    private final Label   alertBanner       = new Label();
    private final Label   demandSummary     = new Label();
    private final Label   demandEmptyState  = new Label();
    private final FlowPane soldHighlights   = new FlowPane();
    private final ComboBox<String> demandRange = new ComboBox<>(
            FXCollections.observableArrayList("Last 7 days","Last 30 days","Last 90 days"));
    private final ComboBox<Medicine> stockMedBox = new ComboBox<>();
    private final TextField medicineSearch = new TextField();

    // ── Stat card labels ─────────────────────────────────────────────────────
    private final Label statTotal    = new Label("0");
    private final Label statLow      = new Label("0");
    private final Label statNear     = new Label("0");
    private final Label statExpired  = new Label("0");
    private final Label statValue    = new Label("₹0");

    // ── Root / Stage ─────────────────────────────────────────────────────────
    private final StackPane root = new StackPane();
    private Stage stage;

    // ─────────────────────────────────────────────────────────────────────────
    //  START
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void start(Stage primaryStage) {
        Database.initialize();
        this.stage = primaryStage;
        root.getStyleClass().add("app-root");
        Scene scene = new Scene(root, 1200, 780);
        scene.getStylesheets().add(
                getClass().getResource("/styles/app.css").toExternalForm());
        primaryStage.setTitle("MediCore Hospital Admin");
        primaryStage.setScene(scene);
        primaryStage.show();
        showAuth();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  AUTH SCREEN  (Login + Register)
    // ─────────────────────────────────────────────────────────────────────────
    private void showAuth() {
        root.getChildren().setAll(buildAuthScreen());
        applyDark(false);
    }

    private Node buildAuthScreen() {
        // ── LEFT HERO ────────────────────────────────────────────────────────
        Label badge = new Label("⚕  MEDICORE SUITE");
        badge.getStyleClass().add("auth-badge");

        StackPane logo = buildLogo();

        // Tab-switching — login vs register hero text managed by TabPane selection
        // We build one hero that adapts
        Label heroTitle = new Label("Hospital Inventory\nCommand Center");
        heroTitle.getStyleClass().add("auth-hero-title");
        heroTitle.setWrapText(true);

        Label heroSub = new Label(
                "Securely manage medicines, stock movements, and clinical operations with audit-ready workflows.");
        heroSub.getStyleClass().add("auth-hero-sub");
        heroSub.setWrapText(true);

        HBox pills = new HBox(8,
                pill("🔒 Pharmacy Safe"), pill("🛡 Role Protected"), pill("📊 Live Reporting"));
        pills.getStyleClass().add("auth-pills");

        Label caption = new Label(
                "Built for hospital administrators, pharmacy leads, and medical operations teams.");
        caption.getStyleClass().add("auth-caption");
        caption.setWrapText(true);

        // Decorative cross watermark
        Label watermark = new Label("⚕");
        watermark.getStyleClass().add("auth-watermark");
        StackPane.setAlignment(watermark, Pos.BOTTOM_RIGHT);

        StackPane heroWrap = new StackPane();
        VBox heroContent = new VBox(18, badge, logo, heroTitle, heroSub, pills, caption);
        heroContent.getStyleClass().add("auth-hero-content");
        heroWrap.getChildren().addAll(heroContent, watermark);
        heroWrap.getStyleClass().add("auth-hero");
        HBox.setHgrow(heroWrap, Priority.ALWAYS);

        // ── RIGHT CARD ───────────────────────────────────────────────────────
        Label cardTitle = new Label("Admin Access");
        cardTitle.getStyleClass().add("auth-card-title");

        Label cardSub = new Label("Sign in to continue or create a new authorized account.");
        cardSub.getStyleClass().add("auth-card-sub");

        // Custom tab bar (LOGIN | REGISTER)
        ToggleGroup tg = new ToggleGroup();
        ToggleButton tbLogin = new ToggleButton("Login");
        tbLogin.getStyleClass().add("auth-tab-btn");
        tbLogin.setToggleGroup(tg);
        tbLogin.setSelected(true);
        ToggleButton tbReg = new ToggleButton("Register");
        tbReg.getStyleClass().add("auth-tab-btn");
        tbReg.setToggleGroup(tg);

        HBox tabBar = new HBox(tbLogin, tbReg);
        tabBar.getStyleClass().add("auth-tab-bar");

        // Form area (swaps between login/register)
        StackPane formArea = new StackPane();
        Node loginForm = buildLoginForm();
        Node registerForm = buildRegisterForm();
        formArea.getChildren().setAll(loginForm);

        // Switch logic
        tbLogin.setOnAction(e -> {
            tbLogin.setSelected(true);
            formArea.getChildren().setAll(loginForm);
            heroTitle.setText("Hospital Inventory\nCommand Center");
            heroSub.setText("Securely manage medicines, stock movements, and clinical operations with audit-ready workflows.");
            pills.getChildren().setAll(pill("🔒 Pharmacy Safe"), pill("🛡 Role Protected"), pill("📊 Live Reporting"));
        });
        tbReg.setOnAction(e -> {
            tbReg.setSelected(true);
            formArea.getChildren().setAll(registerForm);
            heroTitle.setText("Create Your Admin Account");
            heroSub.setText("Register a new administrator account to gain access to the full hospital inventory management suite.");
            pills.getChildren().setAll(pill("✓ Full Access"), pill("✓ Instant Setup"));
        });

        // Auto-select register tab if no users
        if (!repo.hasAnyAdminUser()) {
            tbReg.fire();
        }

        VBox card = new VBox(16, cardTitle, cardSub, tabBar, formArea);
        card.getStyleClass().add("auth-card");
        card.setPrefWidth(480);
        card.setMinWidth(380);

        // ── OUTER WRAPPER ─────────────────────────────────────────────────
        HBox outer = new HBox(heroWrap, card);
        outer.getStyleClass().add("auth-outer");
        outer.setAlignment(Pos.CENTER);

        VBox page = new VBox(outer);
        page.getStyleClass().add("auth-page");
        page.setAlignment(Pos.CENTER);
        VBox.setVgrow(outer, Priority.ALWAYS);
        return page;
    }

    private Node buildLoginForm() {
        Label uLabel = new Label("USERNAME");
        uLabel.getStyleClass().add("form-label");
        TextField uField = new TextField();
        uField.setPromptText("Enter your username");
        uField.getStyleClass().add("form-input");
        uField.setMaxWidth(Double.MAX_VALUE);

        Label pLabel = new Label("PASSWORD");
        pLabel.getStyleClass().add("form-label");
        PasswordField pField = new PasswordField();
        pField.setPromptText("Enter your password");
        pField.getStyleClass().add("form-input");
        pField.setMaxWidth(Double.MAX_VALUE);

        Label hint = new Label("Use your registered administrator credentials.");
        hint.getStyleClass().add("hint-text");

        Button signIn = new Button("→  Sign In");
        signIn.getStyleClass().add("btn-primary");
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setDefaultButton(true);

        // Secure notice
        Label noticeTitle = new Label("🏥  Secure Hospital System");
        noticeTitle.getStyleClass().add("notice-title");
        Label noticeDesc = new Label("All actions are logged for compliance and audit.");
        noticeDesc.getStyleClass().add("notice-desc");
        VBox notice = new VBox(4, noticeTitle, noticeDesc);
        notice.getStyleClass().add("auth-notice");

        signIn.setOnAction(e -> {
            try {
                MedicineRepository.AuthenticatedAdmin admin =
                        repo.authenticateAdmin(uField.getText(), pField.getText());
                if (admin == null) { showErr("Invalid username or password."); return; }
                repo.setActiveAdminUser(admin.id());
                showMain(admin.displayName());
            } catch (Exception ex) { showErr(ex.getMessage()); }
        });

        VBox box = new VBox(10, uLabel, uField, pLabel, pField, hint, signIn, notice);
        box.getStyleClass().add("form-box");
        return box;
    }

    private Node buildRegisterForm() {
        Label fnLabel = new Label("FULL NAME");
        fnLabel.getStyleClass().add("form-label");
        TextField fnField = new TextField();
        fnField.setPromptText("Dr. Full Name");
        fnField.getStyleClass().add("form-input");
        fnField.setMaxWidth(Double.MAX_VALUE);

        Label unLabel = new Label("USERNAME");
        unLabel.getStyleClass().add("form-label");
        TextField unField = new TextField();
        unField.setPromptText("Username (min 4 chars)");
        unField.getStyleClass().add("form-input");
        unField.setMaxWidth(Double.MAX_VALUE);

        Label pwLabel = new Label("PASSWORD");
        pwLabel.getStyleClass().add("form-label");
        PasswordField pwField = new PasswordField();
        pwField.setPromptText("Min 6 chars");
        pwField.getStyleClass().add("form-input");

        Label cpwLabel = new Label("CONFIRM PASSWORD");
        cpwLabel.getStyleClass().add("form-label");
        PasswordField cpwField = new PasswordField();
        cpwField.setPromptText("Confirm");
        cpwField.getStyleClass().add("form-input");

        HBox pwRow = new HBox(12,
                col(pwLabel, pwField), col(cpwLabel, cpwField));
        pwRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pwRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(pwRow.getChildren().get(1), Priority.ALWAYS);

        Button createBtn = new Button("Create Account");
        createBtn.getStyleClass().add("btn-primary");
        createBtn.setMaxWidth(Double.MAX_VALUE);

        createBtn.setOnAction(e -> {
            if (!pwField.getText().equals(cpwField.getText())) {
                showErr("Passwords do not match."); return;
            }
            try {
                repo.registerAdmin(fnField.getText(), unField.getText(), pwField.getText());
                showInfo("Account created! You can now login.");
                fnField.clear(); unField.clear(); pwField.clear(); cpwField.clear();
            } catch (Exception ex) { showErr(ex.getMessage()); }
        });

        VBox box = new VBox(10, fnLabel, fnField, unLabel, unField, pwRow, createBtn);
        box.getStyleClass().add("form-box");
        return box;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MAIN APP
    // ─────────────────────────────────────────────────────────────────────────
    private void showMain(String name) {
        VBox layout = buildMainLayout(name);
        root.getChildren().setAll(layout);
        applyDark(repo.getDarkMode());
        refreshAll();
    }

    private VBox buildMainLayout(String name) {
        // ── TOP BAR ───────────────────────────────────────────────────────
        // Logo icon
        StackPane logoIcon = new StackPane();
        logoIcon.getStyleClass().add("topbar-logo-icon");
        Rectangle h = new Rectangle(14, 5); h.setArcWidth(2); h.setArcHeight(2); h.setFill(Color.WHITE);
        Rectangle v = new Rectangle(5, 14); v.setArcWidth(2); v.setArcHeight(2); v.setFill(Color.WHITE);
        logoIcon.getChildren().addAll(h, v);

        Label appName = new Label("MediCore Operations Console");
        appName.getStyleClass().add("topbar-app-name");

        HBox logoGroup = new HBox(10, logoIcon, appName);
        logoGroup.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label welcome = new Label("Welcome, " + name);
        welcome.getStyleClass().add("topbar-welcome");

        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("btn-logout");
        logoutBtn.setOnAction(e -> { repo.setActiveAdminUser(null); showAuth(); });

        HBox topBar = new HBox(12, logoGroup, spacer, welcome, logoutBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-bar");

        // ── TAB PANE (flat underline style) ──────────────────────────────
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("main-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab tDash = navTab("📊", "Dashboard", buildDashContent());
        Tab tMed  = navTab("💊", "Medicines", buildMedContent());
        Tab tStock= navTab("📦", "Stock", buildStockContent());
        Tab tRep  = navTab("📋", "Reports", buildReportsContent());
        Tab tSet  = navTab("⚙", "Settings", buildSettingsContent());
        tabs.getTabs().addAll(tDash, tMed, tStock, tRep, tSet);

        // Fade animation on tab switch
        tabs.getSelectionModel().selectedItemProperty().addListener((o, old, nw) -> {
            if (nw != null && nw.getContent() != null) fade(nw.getContent());
        });

        VBox.setVgrow(tabs, Priority.ALWAYS);
        VBox main = new VBox(topBar, tabs);
        main.getStyleClass().add("main-layout");
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return main;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DASHBOARD
    // ─────────────────────────────────────────────────────────────────────────
    private Node buildDashContent() {
        // Page title
        Label title = pageTitleLabel("📊", "Admin Overview");

        // ── Stat cards ────────────────────────────────────────────────────
        HBox statsRow = new HBox(14,
                statCard("💊", statTotal, "TOTAL MEDICINES", "green"),
                statCard("⚠️", statLow,   "LOW STOCK",       "amber"),
                statCard("⏰", statNear,  "NEAR EXPIRY",     "rose"),
                statCard("🚫", statExpired,"EXPIRED",         "sky"),
                statCard("💰", statValue, "INVENTORY VALUE", "navy"));
        statsRow.getStyleClass().add("stats-row");

        // ── Alert banner ──────────────────────────────────────────────────
        alertBanner.setWrapText(true);
        alertBanner.getStyleClass().add("alert-banner");

        // ── Chart header ──────────────────────────────────────────────────
        Label chartTitle = new Label("📊 Most Sold Medicines");
        chartTitle.getStyleClass().add("chart-title-label");
        Label chartSubtitle = new Label("Most sold medicines based on OUT transactions.");
        chartSubtitle.getStyleClass().add("chart-subtitle-label");
        VBox chartTitleBox = new VBox(3, chartTitle, chartSubtitle);

        demandRange.getStyleClass().add("form-input-sm");
        demandRange.getSelectionModel().select("Last 30 days");
        demandRange.setOnAction(e -> refreshChart());

        HBox chartHeader = new HBox(chartTitleBox, new Region(), demandRange);
        HBox.setHgrow(chartHeader.getChildren().get(1), Priority.ALWAYS);
        chartHeader.setAlignment(Pos.CENTER_LEFT);
        chartHeader.getStyleClass().add("chart-header");

        demandEmptyState.getStyleClass().add("chart-empty-state");
        demandEmptyState.setWrapText(true);
        demandEmptyState.setAlignment(Pos.CENTER);
        demandEmptyState.setMaxWidth(380);

        demandSummary.getStyleClass().add("chart-summary");
        soldHighlights.getStyleClass().add("sold-highlights");
        soldHighlights.setHgap(12);
        soldHighlights.setVgap(12);
        soldHighlights.setPrefWrapLength(1100);
        soldHighlights.setMaxWidth(Double.MAX_VALUE);

        VBox chartBox = new VBox(12, chartHeader, demandEmptyState, demandSummary, soldHighlights);
        chartBox.getStyleClass().add("chart-container");

        // ── Refresh ───────────────────────────────────────────────────────
        Button refresh = new Button("↻  Refresh");
        refresh.getStyleClass().add("btn-secondary");
        refresh.setOnAction(e -> refreshAll());
        HBox refreshRow = new HBox(refresh);
        refreshRow.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(18, title, statsRow, alertBanner, chartBox, refreshRow);
        panel.getStyleClass().add("panel");

        return scrollWrap(panel);
    }

    private VBox statCard(String icon, Label numLabel, String labelText, String colorClass) {
        Label ico = new Label(icon);
        ico.getStyleClass().addAll("stat-icon", "app-icon");
        numLabel.getStyleClass().addAll("stat-number");
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("stat-label");
        VBox card = new VBox(4, ico, numLabel, lbl);
        card.getStyleClass().addAll("stat-card", "stat-" + colorClass);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MEDICINES
    // ─────────────────────────────────────────────────────────────────────────
    private Node buildMedContent() {
        Label title = pageTitleLabel("💊", "Medicine Inventory");

        // ── Add form section ──────────────────────────────────────────────
        Label addLabel = sectionLabel("➕  ADD NEW MEDICINE");

        Label nL = fl("MEDICINE NAME"), pL = fl("PRICE (₹)"),
              qL = fl("INITIAL QUANTITY"), eL = fl("EXPIRY DATE");
        TextField nameF = fi("e.g. Paracetamol 500mg");
        TextField priceF = fi("0.00");
        TextField qtyF = fi("0"); qtyF.setText("0");
        DatePicker expiryF = new DatePicker(LocalDate.now().plusMonths(6));
        expiryF.getStyleClass().add("form-input");
        expiryF.setMaxWidth(Double.MAX_VALUE);

        GridPane addGrid = new GridPane();
        addGrid.setHgap(16); addGrid.setVgap(8);
        addGrid.getColumnConstraints().addAll(col50(), col50());
        addGrid.addRow(0, col(nL, nameF), col(pL, priceF));
        addGrid.addRow(1, col(qL, qtyF), col(eL, expiryF));

        Button addBtn = new Button("➕  Add Medicine");  addBtn.getStyleClass().add("btn-primary");
        Button editBtn = new Button("✏️  Edit Selected"); editBtn.getStyleClass().add("btn-secondary");
        Button delBtn = new Button("🗑  Delete Selected"); delBtn.getStyleClass().add("btn-danger");
        HBox btnRow = new HBox(10, addBtn, editBtn, delBtn);

        addBtn.setOnAction(e -> {
            try {
                repo.addMedicine(nameF.getText().trim(),
                        Integer.parseInt(qtyF.getText().trim()),
                        Double.parseDouble(priceF.getText().trim()),
                        expiryF.getValue());
                nameF.clear(); priceF.clear(); qtyF.setText("0");
                expiryF.setValue(LocalDate.now().plusMonths(6));
                refreshAll();
            } catch (Exception ex) { showErr(ex.getMessage()); }
        });
        editBtn.setOnAction(e -> openEditDialog());
        delBtn.setOnAction(e -> {
            Medicine sel = medicineTable.getSelectionModel().getSelectedItem();
            if (sel == null) { showErr("Select a medicine first."); return; }
            repo.deleteMedicine(sel.id()); refreshAll();
        });

        VBox addForm = new VBox(12, addLabel, addGrid, btnRow);
        addForm.getStyleClass().add("form-section");

        // ── Search row ────────────────────────────────────────────────────
        Label findLbl = new Label("🔍  Find");
        findLbl.getStyleClass().add("find-label");
        medicineSearch.setPromptText("Search medicine by name...");
        medicineSearch.getStyleClass().add("form-input");
        HBox.setHgrow(medicineSearch, Priority.ALWAYS);
        Button searchBtn = new Button("Search"); searchBtn.getStyleClass().add("btn-secondary");
        Button clearBtn  = new Button("Clear");  clearBtn.getStyleClass().add("btn-secondary");
        searchBtn.setOnAction(e -> refreshMedicines());
        clearBtn.setOnAction(e -> { medicineSearch.clear(); refreshMedicines(); });
        HBox searchRow = new HBox(10, findLbl, medicineSearch, searchBtn, clearBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.getStyleClass().add("search-row");

        // ── Table ─────────────────────────────────────────────────────────
        buildMedicineColumns();
        medicineTable.getStyleClass().add("data-table");
        medicineTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        medicineTable.setPrefHeight(380);
        VBox.setVgrow(medicineTable, Priority.ALWAYS);

        VBox panel = new VBox(18, title, addForm, searchRow, medicineTable);
        panel.getStyleClass().add("panel");
        VBox.setVgrow(medicineTable, Priority.ALWAYS);

        return scrollWrap(panel);
    }

    @SuppressWarnings("unchecked")
    private void buildMedicineColumns() {
        TableColumn<Medicine, Long>   idCol  = tc2("ID");
        idCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().id()));
        idCol.setPrefWidth(60);

        TableColumn<Medicine, String> nameCol = tc2("NAME");
        nameCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().name()));

        TableColumn<Medicine, Integer> qtyCol = tc2("QUANTITY");
        qtyCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().quantity()));

        TableColumn<Medicine, Double> priceCol = tc2("PRICE (₹)");
        priceCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().price()));

        TableColumn<Medicine, String> expCol = tc2("EXPIRY");
        expCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().expiryDate().toString()));

        TableColumn<Medicine, String> statusCol = tc2("STATUS");
        statusCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(statusText(v.getValue())));
        // Coloured badge cell
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(s);
                badge.getStyleClass().addAll("badge", badgeClass(s));
                setGraphic(badge); setText(null);
            }
        });

        medicineTable.getColumns().setAll(idCol, nameCol, qtyCol, priceCol, expCol, statusCol);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  STOCK
    // ─────────────────────────────────────────────────────────────────────────
    private Node buildStockContent() {
        Label title = pageTitleLabel("📦", "Stock Movements");
        Label sectionLbl = sectionLabel("📝  NEW TRANSACTION");

        // Medicine selector card
        Label medLabel = fl("SELECT MEDICINE");
        stockMedBox.getStyleClass().add("form-input");
        stockMedBox.setMaxWidth(Double.MAX_VALUE);
        stockMedBox.setConverter(new StringConverter<>() {
            @Override public String toString(Medicine m) {
                return m == null ? "" : m.name() + " (Qty: " + m.quantity() + ")";
            }
            @Override public Medicine fromString(String s) { return null; }
        });
        VBox medSel = new VBox(6, medLabel, stockMedBox);
        medSel.getStyleClass().add("med-select-wrap");

        // Transaction type chips
        Label typeLabel = fl("TRANSACTION TYPE");
        ToggleGroup tg = new ToggleGroup();
        ToggleButton inBtn  = new ToggleButton("↓ IN");
        inBtn.getStyleClass().add("type-chip-in");
        inBtn.setToggleGroup(tg); inBtn.setSelected(true);
        ToggleButton outBtn = new ToggleButton("↑ OUT");
        outBtn.getStyleClass().add("type-chip-out");
        outBtn.setToggleGroup(tg);
        HBox typeChips = new HBox(8, inBtn, outBtn);
        VBox typeCol2 = new VBox(6, typeLabel, typeChips);

        // Qty / Ref / Note
        Label qtyLabel = fl("QUANTITY");
        TextField qtyField = fi("0");
        qtyField.setPrefWidth(100);

        Label refLabel = fl("REFERENCE NO.");
        TextField refField = fi("e.g. PO-2024-001");
        HBox.setHgrow(refField, Priority.ALWAYS);

        Label noteLabel = fl("NOTE");
        TextField noteField = fi("Optional note");
        HBox.setHgrow(noteField, Priority.ALWAYS);

        VBox qtyCol = col(qtyLabel, qtyField);
        VBox refCol = col(refLabel, refField);
        VBox noteCol = col(noteLabel, noteField);

        GridPane txGrid = new GridPane();
        txGrid.setHgap(14); txGrid.setVgap(8);
        txGrid.getColumnConstraints().addAll(col100(), col100(), col100(), col100());
        txGrid.addRow(0, typeCol2, qtyCol, refCol, noteCol);

        Button saveBtn = new Button("💾  Save Transaction");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            try {
                Medicine sel = stockMedBox.getValue();
                if (sel == null) { showErr("Select medicine."); return; }
                int qty = Integer.parseInt(qtyField.getText().trim());
                if (inBtn.isSelected())
                    repo.restock(sel.id(), qty, refField.getText().trim(), noteField.getText().trim());
                else
                    repo.consume(sel.id(), qty, refField.getText().trim(), noteField.getText().trim());
                qtyField.clear(); refField.clear(); noteField.clear();
                refreshAll();
            } catch (Exception ex) { showErr(ex.getMessage()); }
        });

        VBox txForm = new VBox(12, sectionLbl, medSel, txGrid, saveBtn);
        txForm.getStyleClass().add("form-section");

        // ── Movement table ────────────────────────────────────────────────
        buildMovementColumns();
        movementTable.getStyleClass().add("data-table");
        movementTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        movementTable.setPrefHeight(360);

        VBox panel = new VBox(18, title, txForm, movementTable);
        panel.getStyleClass().add("panel");
        return scrollWrap(panel);
    }

    @SuppressWarnings("unchecked")
    private void buildMovementColumns() {
        TableColumn<StockMovement, Long>   idCol  = tc2("TX ID");
        idCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().id()));
        idCol.setPrefWidth(70);

        TableColumn<StockMovement, String> medCol  = tc2("MEDICINE");
        medCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().medicineName()));

        TableColumn<StockMovement, String> typeCol = tc2("TYPE");
        typeCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().type()));
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                Label b = new Label("IN".equals(s) ? "↓ IN" : "↑ OUT");
                b.getStyleClass().addAll("badge", "IN".equals(s) ? "badge-green" : "badge-rose");
                setGraphic(b); setText(null);
            }
        });

        TableColumn<StockMovement, Integer> qtyCol = tc2("QTY");
        qtyCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().quantity()));

        TableColumn<StockMovement, String> refCol = tc2("REFERENCE");
        refCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(dash(v.getValue().referenceNo())));

        TableColumn<StockMovement, String> noteCol = tc2("NOTE");
        noteCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(dash(v.getValue().note())));

        TableColumn<StockMovement, String> tsCol = tc2("CREATED AT");
        tsCol.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().createdAt()));

        movementTable.getColumns().setAll(idCol, medCol, typeCol, qtyCol, refCol, noteCol, tsCol);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  REPORTS
    // ─────────────────────────────────────────────────────────────────────────
    private Node buildReportsContent() {
        Label title = pageTitleLabel("📋", "Export Reports");
        Label sub = new Label("Export stock, expiry, and transaction reports to CSV and PDF formats.");
        sub.getStyleClass().add("page-subtitle");

        // 2×2 report cards grid
        ComboBox<String> rangeBox = new ComboBox<>(
                FXCollections.observableArrayList("This Week", "This Month", "This Year"));
        rangeBox.getSelectionModel().select("This Month");
        rangeBox.getStyleClass().add("form-input-sm");

        GridPane grid = new GridPane();
        grid.setHgap(16); grid.setVgap(16);
        grid.getColumnConstraints().addAll(col50(), col50());

        grid.add(reportCard("📦", "Current Stock Report",
                "Full snapshot of current medicine inventory with quantities, prices, and expiry dates.",
                "⬇  Export CSV + PDF", "btn-primary", e -> exportStock()), 0, 0);
        grid.add(reportCard("⏰", "Near Expiry Report",
                "Medicines expiring within the next 30 days. Critical for compliance and waste prevention.",
                "⬇  Export CSV + PDF", "btn-secondary", e -> exportNearExpiry()), 1, 0);
        grid.add(reportCard("🔄", "All Stock Transactions",
                "Complete audit log of all IN/OUT transactions — full history for compliance reporting.",
                "⬇  Export CSV + PDF", "btn-secondary", e -> exportAllTx()), 0, 1);

        // Ranged report card (special)
        Label rIcon  = new Label("📅"); rIcon.getStyleClass().addAll("report-icon", "app-icon");
        Label rTitle = new Label("Ranged Transaction Report"); rTitle.getStyleClass().add("report-title");
        Label rDesc  = new Label("Filter transactions by time range — this week, month, or year for targeted analysis.");
        rDesc.getStyleClass().add("report-desc"); rDesc.setWrapText(true);
        Button rBtn = new Button("⬇  Export"); rBtn.getStyleClass().add("btn-secondary");
        rBtn.setOnAction(e -> exportRangedTx(rangeBox.getValue()));
        HBox rBtnRow = new HBox(10, rangeBox, rBtn);
        rBtnRow.setAlignment(Pos.CENTER_LEFT);
        VBox rCard = new VBox(10, rIcon, rTitle, rDesc, rBtnRow);
        rCard.getStyleClass().add("report-card");
        rCard.setMaxWidth(Double.MAX_VALUE);
        grid.add(rCard, 1, 1);

        VBox panel = new VBox(14, title, sub, grid);
        panel.getStyleClass().add("panel");
        return scrollWrap(panel);
    }

    private VBox reportCard(String icon, String titleTxt, String desc,
                             String btnTxt, String btnStyle,
                             javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Label ico  = new Label(icon); ico.getStyleClass().addAll("report-icon", "app-icon");
        Label ttl  = new Label(titleTxt); ttl.getStyleClass().add("report-title");
        Label dsc  = new Label(desc); dsc.getStyleClass().add("report-desc"); dsc.setWrapText(true);
        Button btn = new Button(btnTxt); btn.getStyleClass().add(btnStyle);
        btn.setOnAction(handler);
        VBox card = new VBox(10, ico, ttl, dsc, btn);
        card.getStyleClass().add("report-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void exportStock() {
        File f = pickCsv("stock-report.csv"); if (f == null) return;
        try { reportService.exportCurrentStockCsv(f, repo.findAllMedicines());
              reportService.exportCurrentStockPdf(sibling(f, ".pdf"), repo.findAllMedicines());
              showInfo("Stock report exported."); }
        catch (Exception e) { showErr(e.getMessage()); }
    }
    private void exportNearExpiry() {
        File f = pickCsv("near-expiry.csv"); if (f == null) return;
        try { var l = repo.findNearExpiry(30);
              reportService.exportNearExpiryCsv(f, l);
              reportService.exportNearExpiryPdf(sibling(f,".pdf"), l);
              showInfo("Near-expiry report exported."); }
        catch (Exception e) { showErr(e.getMessage()); }
    }
    private void exportAllTx() {
        File f = pickCsv("transactions.csv"); if (f == null) return;
        try { var l = repo.findRecentMovements(100000);
              reportService.exportStockMovementsCsv(f, l);
              reportService.exportStockMovementsPdf(sibling(f,".pdf"), l, "All");
              showInfo("Transactions exported."); }
        catch (Exception e) { showErr(e.getMessage()); }
    }
    private void exportRangedTx(String range) {
        File f = pickCsv("transactions-ranged.csv"); if (f == null) return;
        try { var l = filterByRange(repo.findRecentMovements(100000), range);
              reportService.exportStockMovementsCsv(f, l);
              reportService.exportStockMovementsPdf(sibling(f,".pdf"), l, range);
              showInfo("Ranged report exported."); }
        catch (Exception e) { showErr(e.getMessage()); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SETTINGS
    // ─────────────────────────────────────────────────────────────────────────
    private Node buildSettingsContent() {
        Label title = pageTitleLabel("⚙️", "System Settings");

        // ── ALERT SETTINGS card ───────────────────────────────────────────
        Label alertSectionTitle = new Label("🔔  ALERT SETTINGS");
        alertSectionTitle.getStyleClass().add("settings-section-title");

        // Low Stock Alerts row (with toggle)
        Label alertLabel = new Label("Low Stock Alerts");
        alertLabel.getStyleClass().add("settings-label");
        Label alertDesc = new Label("Notify when medicine quantity falls below threshold");
        alertDesc.getStyleClass().add("settings-desc");
        VBox alertTextBox = new VBox(2, alertLabel, alertDesc);
        ToggleButton alertToggle = buildToggle(repo.getNotifyLowStock());
        HBox alertRow = new HBox(alertTextBox, new Region(), alertToggle);
        HBox.setHgrow(alertRow.getChildren().get(1), Priority.ALWAYS);
        alertRow.setAlignment(Pos.CENTER_LEFT);
        alertRow.getStyleClass().add("settings-row");

        // Threshold
        Label threshLabel = new Label("Low Stock Threshold");
        threshLabel.getStyleClass().add("settings-label");
        TextField threshField = fi("10"); threshField.setText(String.valueOf(repo.getLowStockThreshold()));
        threshField.setMaxWidth(80);
        Label units = new Label("units"); units.getStyleClass().add("units-label");
        HBox threshRow2 = new HBox(8, threshField, units);
        VBox threshRow = new VBox(8, threshLabel, threshRow2);
        threshRow.getStyleClass().add("settings-row");

        VBox alertCard = new VBox(0, alertSectionTitle, alertRow, threshRow);
        alertCard.getStyleClass().add("settings-card");

        // ── APPEARANCE card ───────────────────────────────────────────────
        Label appearSection = new Label("🎨  APPEARANCE");
        appearSection.getStyleClass().add("settings-section-title");

        Label dmLabel = new Label("Dark Mode");
        dmLabel.getStyleClass().add("settings-label");
        Label dmDesc = new Label("Switch to dark theme for low-light environments");
        dmDesc.getStyleClass().add("settings-desc");
        VBox dmText = new VBox(2, dmLabel, dmDesc);
        ToggleButton dmToggle = buildToggle(repo.getDarkMode());
        dmToggle.selectedProperty().addListener((o, ov, nv) -> applyDark(nv));
        HBox dmRow = new HBox(dmText, new Region(), dmToggle);
        HBox.setHgrow(dmRow.getChildren().get(1), Priority.ALWAYS);
        dmRow.setAlignment(Pos.CENTER_LEFT);
        dmRow.getStyleClass().add("settings-row");

        VBox appearCard = new VBox(0, appearSection, dmRow);
        appearCard.getStyleClass().add("settings-card");

        // ── SYSTEM INFO card ──────────────────────────────────────────────
        Label sysSection = new Label("ℹ️  SYSTEM INFO");
        sysSection.getStyleClass().add("settings-section-title");

        HBox sysInfo = new HBox(12,
                infoTile("v2.4.1", "VERSION"),
                infoTile("SQLite",  "DATABASE"),
                infoTile("JavaFX",  "PLATFORM"),
                infoTile("MediCore","PRODUCT"));
        sysInfo.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        VBox sysCard = new VBox(12, sysSection, sysInfo);
        sysCard.getStyleClass().addAll("settings-card","settings-card-full");

        // ── Settings 2-col grid ───────────────────────────────────────────
        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(16); settingsGrid.setVgap(16);
        settingsGrid.getColumnConstraints().addAll(col50(), col50());
        settingsGrid.add(alertCard, 0, 0);
        settingsGrid.add(appearCard, 1, 0);
        settingsGrid.add(sysCard, 0, 1);
        GridPane.setColumnSpan(sysCard, 2);

        Button saveBtn = new Button("💾  Save Settings");
        saveBtn.getStyleClass().add("btn-primary");
        HBox saveRow = new HBox(saveBtn);
        saveRow.setAlignment(Pos.CENTER_RIGHT);

        saveBtn.setOnAction(e -> {
            try {
                repo.setNotifyLowStock(alertToggle.isSelected());
                repo.setLowStockThreshold(Integer.parseInt(threshField.getText().trim()));
                repo.setDarkMode(dmToggle.isSelected());
                applyDark(dmToggle.isSelected());
                showInfo("Settings saved.");
                refreshAll();
            } catch (Exception ex) { showErr(ex.getMessage()); }
        });

        VBox panel = new VBox(18, title, settingsGrid, saveRow);
        panel.getStyleClass().add("panel");
        return scrollWrap(panel);
    }

    private ToggleButton buildToggle(boolean on) {
        ToggleButton t = new ToggleButton();
        t.setSelected(on);
        t.getStyleClass().add("toggle-btn");
        t.selectedProperty().addListener((o, ov, nv) ->
                t.getStyleClass().setAll("toggle-btn", nv ? "toggle-on" : "toggle-off"));
        t.getStyleClass().add(on ? "toggle-on" : "toggle-off");
        return t;
    }

    private VBox infoTile(String val, String lbl) {
        Label v = new Label(val); v.getStyleClass().add("info-tile-val");
        Label l = new Label(lbl); l.getStyleClass().add("info-tile-label");
        VBox tile = new VBox(4, v, l);
        tile.getStyleClass().add("info-tile");
        tile.setAlignment(Pos.CENTER);
        return tile;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EDIT MEDICINE DIALOG
    // ─────────────────────────────────────────────────────────────────────────
    private void openEditDialog() {
        Medicine sel = medicineTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showErr("Select a medicine first."); return; }

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Edit Medicine");
        dlg.getDialogPane().getButtonTypes().addAll(
                new ButtonType("💾  Save Changes", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL);
        dlg.getDialogPane().getStyleClass().add("medicore-dialog");
        dlg.getDialogPane().getScene().getStylesheets().add(
                getClass().getResource("/styles/app.css").toExternalForm());

        Label nL = fl("MEDICINE NAME"); TextField nF = fi(sel.name()); nF.setText(sel.name());
        Label pL = fl("PRICE (₹)");    TextField pF = fi(""); pF.setText(String.valueOf(sel.price()));
        Label eL = fl("EXPIRY DATE");  DatePicker eF = new DatePicker(sel.expiryDate());
        eF.getStyleClass().add("form-input"); eF.setMaxWidth(Double.MAX_VALUE);

        // Low stock warning
        VBox warning = new VBox();
        if (sel.quantity() <= repo.getLowStockThreshold()) {
            Label w = new Label("⚠️  This medicine is currently low stock (" + sel.quantity() + " units)");
            w.getStyleClass().add("dialog-warning");
            w.setWrapText(true);
            warning.getChildren().add(w);
        }

        VBox content = new VBox(14,
                col(nL, nF), col(pL, pF), col(eL, eF), warning);
        content.setPadding(new Insets(4, 0, 0, 0));
        dlg.getDialogPane().setContent(content);

        dlg.showAndWait().ifPresent(r -> {
            if (r.getButtonData() != ButtonBar.ButtonData.OK_DONE) return;
            try {
                repo.updateMedicine(sel.id(), nF.getText().trim(),
                        Double.parseDouble(pF.getText().trim()), eF.getValue());
                refreshAll();
            } catch (Exception ex) { showErr(ex.getMessage()); }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  REFRESH
    // ─────────────────────────────────────────────────────────────────────────
    private void refreshAll() { refreshMedicines(); refreshMovements(); refreshStats(); refreshChart(); }

    private void refreshMedicines() {
        String q = medicineSearch.getText() == null ? "" : medicineSearch.getText().trim().toLowerCase();
        var list = repo.findAllMedicines();
        if (!q.isEmpty()) list = list.stream().filter(m -> m.name().toLowerCase().contains(q)).toList();
        medicineTable.setItems(FXCollections.observableArrayList(list));
        stockMedBox.setItems(FXCollections.observableArrayList(repo.findAllMedicines()));
        if (stockMedBox.getValue() == null && !stockMedBox.getItems().isEmpty())
            stockMedBox.getSelectionModel().selectFirst();
    }

    private void refreshMovements() {
        movementTable.setItems(FXCollections.observableArrayList(repo.findRecentMovements(100)));
    }

    private void refreshStats() {
        var list = repo.findAllMedicines();
        long total   = list.size();
        long low     = repo.findLowStockMedicines(repo.getLowStockThreshold()).size();
        long near    = list.stream()
                .filter(m -> !m.expiryDate().isBefore(LocalDate.now())
                          && !m.expiryDate().isAfter(LocalDate.now().plusDays(30))).count();
        long expired = list.stream().filter(m -> m.expiryDate().isBefore(LocalDate.now())).count();
        double val   = list.stream().mapToDouble(m -> m.quantity() * m.price()).sum();

        statTotal.setText(String.valueOf(total));
        statLow.setText(String.valueOf(low));
        statNear.setText(String.valueOf(near));
        statExpired.setText(String.valueOf(expired));
        statValue.setText(val >= 100000 ? String.format("₹%.1fL", val / 100000)
                                        : String.format("₹%.0f", val));

        if (!repo.getNotifyLowStock()) {
            alertBanner.setText("Low-stock alerts are disabled in settings.");
            alertBanner.setVisible(true);
            return;
        }
        var ls = repo.findLowStockMedicines(repo.getLowStockThreshold());
        if (ls.isEmpty()) {
            alertBanner.setVisible(false);
        } else {
            String names = ls.stream().limit(4).map(Medicine::name).collect(Collectors.joining(", "));
            alertBanner.setText("⚠️  Low Stock Alert (threshold: " + repo.getLowStockThreshold()
                    + "): " + ls.size() + " items — " + names + "...");
            alertBanner.setVisible(true);
        }
    }

    private void refreshChart() {
        int days = "Last 7 days".equals(demandRange.getValue()) ? 7
                 : "Last 90 days".equals(demandRange.getValue()) ? 90 : 30;
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusDays(days - 1L);

        List<StockMovement> movements = repo.findRecentMovements(10000);
        List<StockMovement> outMovements = movements.stream()
                .filter(m -> "OUT".equalsIgnoreCase(m.type()))
                .filter(m -> {
                    LocalDate d = parseDate(m.createdAt());
                    return d != null && !d.isBefore(cutoff) && !d.isAfter(today);
                })
                .toList();

        Map<String, Integer> demand = new LinkedHashMap<>();
        for (StockMovement m : outMovements) {
            demand.merge(m.medicineName(), m.quantity(), Integer::sum);
        }

        var top = demand.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(8)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        soldHighlights.getChildren().clear();

        if (!top.isEmpty()) {
            int totalConsumed = top.values().stream().mapToInt(Integer::intValue).sum();
            Map.Entry<String, Integer> peak = top.entrySet().iterator().next();
            demandEmptyState.setVisible(false);
            demandEmptyState.setManaged(false);
            soldHighlights.setVisible(true);
            soldHighlights.setManaged(true);
            demandSummary.setText(String.format(
                    "Best seller: %s with %d units sold. Total sold: %d units across %d medicine(s) from %s to %s.",
                    peak.getKey(), peak.getValue(), totalConsumed, top.size(), cutoff, today));
            buildSoldHighlights(top, totalConsumed);
        } else {
            demandEmptyState.setVisible(true);
            demandEmptyState.setManaged(true);
            soldHighlights.setVisible(false);
            soldHighlights.setManaged(false);

            Optional<LocalDate> latestOutDate = movements.stream()
                    .filter(m -> "OUT".equalsIgnoreCase(m.type()))
                    .map(m -> parseDate(m.createdAt()))
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo);

            if (latestOutDate.isPresent()) {
                demandEmptyState.setText(String.format(
                        "No sales recorded from %s to %s.%nTry Last 90 days or add a new OUT transaction.",
                        cutoff, today));
                demandSummary.setText("This chart uses OUT transactions only. Latest sale recorded on: " + latestOutDate.get() + ".");
            } else {
                demandEmptyState.setText("No sales recorded yet.\nAdd an OUT stock movement to see the most sold medicines.");
                demandSummary.setText("This chart uses OUT transactions only.");
            }
        }
    }

    private void buildSoldHighlights(Map<String, Integer> top, int totalConsumed) {
        int rank = 1;
        for (Map.Entry<String, Integer> entry : top.entrySet()) {
            soldHighlights.getChildren().add(soldHighlightCard(rank++, entry.getKey(), entry.getValue(), totalConsumed));
        }
    }

    private VBox soldHighlightCard(int rank, String medicine, int qty, int totalConsumed) {
        Label rankLabel = new Label("#" + rank);
        rankLabel.getStyleClass().add("sold-highlight-rank");

        Label nameLabel = new Label(medicine);
        nameLabel.getStyleClass().add("sold-highlight-name");
        nameLabel.setWrapText(true);

        double share = totalConsumed <= 0 ? 0 : (qty * 100.0) / totalConsumed;
        Label qtyLabel = new Label(String.format("%d units sold • %.1f%% share", qty, share));
        qtyLabel.getStyleClass().add("sold-highlight-qty");

        VBox card = new VBox(6, rankLabel, nameLabel, qtyLabel);
        card.getStyleClass().add("sold-highlight-card");
        card.setPrefWidth(220);
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DARK MODE
    // ─────────────────────────────────────────────────────────────────────────
    private void applyDark(boolean dark) {
        root.getStyleClass().removeAll("dark-mode");
        if (dark) root.getStyleClass().add("dark-mode");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS / FACTORIES
    // ─────────────────────────────────────────────────────────────────────────
    private Tab navTab(String icon, String text, Node content) {
        Tab tab = new Tab(text, content);
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().addAll("tab-icon", "app-icon");
        tab.setGraphic(iconLabel);
        return tab;
    }

    private Label pageTitleLabel(String icon, String text) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().addAll("page-title-icon-glyph", "app-icon");
        StackPane ico = new StackPane(iconLabel);
        ico.getStyleClass().add("page-title-icon");
        Label lbl = new Label(text);
        lbl.getStyleClass().add("page-title-text");
        HBox h = new HBox(10, ico, lbl);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("page-title");
        // Return as label-like — wrap in a Label proxy via setGraphic
        Label proxy = new Label();
        proxy.setGraphic(h);
        proxy.getStyleClass().add("page-title-proxy");
        return proxy;
    }

    private Label sectionLabel(String t) {
        Label l = new Label(t); l.getStyleClass().add("section-label"); return l;
    }
    private Label fl(String t)  { Label l = new Label(t); l.getStyleClass().add("form-label"); return l; }
    private TextField fi(String ph) {
        TextField f = new TextField(); f.setPromptText(ph);
        f.getStyleClass().add("form-input"); f.setMaxWidth(Double.MAX_VALUE); return f;
    }
    private Label pill(String t) { Label l = new Label(t); l.getStyleClass().add("auth-pill"); return l; }

    private VBox col(Label lbl, Control field) {
        VBox v = new VBox(5, lbl, field); v.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(field, Priority.NEVER);
        return v;
    }

    private <T> TableColumn<T, ?> tc(String title) {
        TableColumn<T, ?> col = new TableColumn<>(title);
        col.getStyleClass().add("table-col");
        return col;
    }
    @SuppressWarnings("unchecked")
    private <T, V> TableColumn<T, V> tc2(String title) { return (TableColumn<T, V>) tc(title); }

    private StackPane buildLogo() {
        Circle ring = new Circle(34);
        ring.setFill(Color.web("rgba(255,255,255,0.70)"));
        ring.setStroke(Color.web("rgba(0,191,165,0.45)"));
        ring.setStrokeWidth(2.5);
        Rectangle v = new Rectangle(10, 28); v.setArcWidth(5); v.setArcHeight(5); v.setFill(Color.web("#00897b"));
        Rectangle h = new Rectangle(28, 10); h.setArcWidth(5); h.setArcHeight(5); h.setFill(Color.web("#00897b"));
        Line l1 = new Line(-20, 6, -9, -4);  l1.setStroke(Color.web("#006064")); l1.setStrokeWidth(2.2);
        Line l2 = new Line(-9, -4, 4, 8);    l2.setStroke(Color.web("#006064")); l2.setStrokeWidth(2.2);
        Line l3 = new Line(4, 8, 18, -6);    l3.setStroke(Color.web("#006064")); l3.setStrokeWidth(2.2);
        StackPane logo = new StackPane(ring, v, h, l1, l2, l3);
        logo.getStyleClass().add("auth-logo");
        logo.setPrefSize(80, 80); logo.setMaxSize(80, 80);
        return logo;
    }

    private ScrollPane scrollWrap(Node content) {
        VBox shell = new VBox(content);
        shell.getStyleClass().add("tab-content-bg");
        shell.setFillWidth(true);
        shell.setPadding(new Insets(18, 18, 24, 18));
        if (content instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }

        ScrollPane sp = new ScrollPane(shell);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.getStyleClass().add("content-scroll");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) ->
                shell.setMinHeight(newBounds.getHeight()));
        return sp;
    }

    private void fade(Node n) {
        FadeTransition ft = new FadeTransition(Duration.millis(200), n);
        ft.setFromValue(0.7); ft.setToValue(1.0); ft.play();
    }

    private ColumnConstraints col50() {
        ColumnConstraints c = new ColumnConstraints();
        c.setPercentWidth(50); c.setHgrow(Priority.ALWAYS); return c;
    }
    private ColumnConstraints col100() {
        ColumnConstraints c = new ColumnConstraints();
        c.setHgrow(Priority.ALWAYS); return c;
    }

    private String statusText(Medicine m) {
        if (m.quantity() <= 0) return "✕ Out of Stock";
        if (m.expiryDate().isBefore(LocalDate.now())) return "✕ Expired";
        long d = ChronoUnit.DAYS.between(LocalDate.now(), m.expiryDate());
        if (d <= 30) return "⏰ Near Expiry";
        if (m.quantity() <= repo.getLowStockThreshold()) return "⚠ Low Stock";
        return "✓ Available";
    }

    private String badgeClass(String status) {
        if (status.contains("Available"))   return "badge-green";
        if (status.contains("Low Stock"))   return "badge-amber";
        if (status.contains("Near Expiry")) return "badge-amber";
        if (status.contains("Expired"))     return "badge-rose";
        if (status.contains("Out"))         return "badge-rose";
        return "badge-sky";
    }

    private String dash(String v) { return v == null || v.isBlank() ? "—" : v; }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s, TS_FMT).toLocalDate(); }
        catch (Exception e) { return null; }
    }

    private List<StockMovement> filterByRange(List<StockMovement> list, String range) {
        LocalDate today = LocalDate.now();
        LocalDate start = switch (range) {
            case "This Week" -> today.with(java.time.DayOfWeek.MONDAY);
            case "This Year" -> LocalDate.of(today.getYear(), 1, 1);
            default          -> LocalDate.of(today.getYear(), today.getMonthValue(), 1);
        };
        return list.stream().filter(m -> {
            LocalDate d = parseDate(m.createdAt());
            return d != null && !d.isBefore(start) && !d.isAfter(today);
        }).toList();
    }

    private File pickCsv(String name) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Report"); fc.setInitialFileName(name);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        return fc.showSaveDialog(stage);
    }

    private File sibling(File f, String ext) {
        String n = f.getName(); int dot = n.lastIndexOf('.');
        return new File(f.getParentFile(), (dot > 0 ? n.substring(0, dot) : n) + ext);
    }

    private void showErr(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Error"); a.initOwner(stage); a.showAndWait();
    }
    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText("Info"); a.initOwner(stage); a.showAndWait();
    }

    public static void main(String[] args) { launch(); }
}
