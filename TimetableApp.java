/* 전체적인 틀과 코드를 정리하는 과정에서 ai를 활용하였습니다 */
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.xml.parsers.*;
class Course {
    String dept, type, code, name, prof, day, room;
    int credit, startMin, endMin, grade;
    String baseName;

    Course(String dept, String type, String code, String name, int credit, String prof, String day, int startMin, int endMin, String room, int grade) {
        this.dept = dept; this.type = type; this.code = code; this.name = name;
        this.credit = credit; this.prof = prof; this.day = day;
        this.startMin = startMin; this.endMin = endMin; this.room = room;
        this.grade = grade;
        this.baseName = name.replaceAll("[-_]\\d+$", "").trim();
    }

    boolean conflicts(Course o) {
        if (!this.day.equals(o.day)) return false;
        if (this.endMin <= o.startMin) return false;
        if (o.endMin <= this.startMin) return false;
        return true;
    }

    String getTimeStr() {
        return day + " " + String.format("%02d:%02d", startMin/60, startMin%60)
                   + "-" + String.format("%02d:%02d", endMin/60, endMin%60);
    }

    public String toString() {
        return name + " (" + prof + ") " + getTimeStr() + " [" + room + "] " + credit + "학점";
    }
}

class Schedule {
    ArrayList<Course> list = new ArrayList<Course>();
    int totalCredit = 0, score = 0;
    String label = "";

    void addCourse(Course c) { list.add(c); totalCredit += c.credit; }

    boolean hasConflict(Course c) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).conflicts(c)) return true;
        }
        return false;
    }

    Course getConflictWith(Course c) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).conflicts(c)) return list.get(i);
        }
        return null;
    }

    Schedule copy() {
        Schedule s = new Schedule();
        for (int i = 0; i < list.size(); i++) s.list.add(list.get(i));
        s.totalCredit = this.totalCredit;
        s.score = this.score;
        return s;
    }
}

class Engine {
    ArrayList<Schedule> results = new ArrayList<Schedule>();
    int targetCredit;
    int mustSize = 0;
    ArrayList<String> strategies = new ArrayList<String>();
    int maxCredit = 18;

    void generate(ArrayList<Course> mustHave, ArrayList<Course> optional, int target, int maxCr, ArrayList<String> strats) {
        results.clear();
        this.targetCredit = target;
        this.maxCredit = maxCr;
        this.strategies = strats;

        mustSize = mustHave.size();

        Schedule base = new Schedule();
        for (int i = 0; i < mustHave.size(); i++) {
            base.addCourse(mustHave.get(i));
        }

        ArrayList<ArrayList<Course>> groups = groupByBaseName(optional);
        dfs(groups, 0, base);

        if (results.isEmpty() && !mustHave.isEmpty()) {
            Schedule onlyMust = base.copy();
            onlyMust.score = calcScore(onlyMust);
            results.add(onlyMust);
        }
    }

    ArrayList<ArrayList<Course>> groupByBaseName(ArrayList<Course> list) {
        LinkedHashMap<String, ArrayList<Course>> map = new LinkedHashMap<String, ArrayList<Course>>();
        for (int i = 0; i < list.size(); i++) {
            Course c = list.get(i);
            String key = c.dept + "_" + c.baseName + "_" + c.day + "_" + c.startMin;
            if (!map.containsKey(key)) map.put(key, new ArrayList<Course>());
            map.get(key).add(c);
        }
        ArrayList<ArrayList<Course>> groups = new ArrayList<ArrayList<Course>>();
        for (ArrayList<Course> g : map.values()) groups.add(g);
        return groups;
    }

        //ai활용 시작 - 백트래킹 재귀 알고리즘으로 시간표 조합 생성
    private void dfs(ArrayList<ArrayList<Course>> groups, int idx, Schedule cur) {
        if (results.size() >= 300) return;

        if (idx >= groups.size()) {
            if (cur.list.size() >= mustSize) {
                Schedule copy = cur.copy();
                copy.score = calcScore(copy);
                results.add(copy);
            }
            return;
        }

        ArrayList<Course> group = groups.get(idx);
        for (int i = 0; i < group.size(); i++) {
            Course c = group.get(i);
            if (cur.totalCredit + c.credit > maxCredit) continue;
            if (!cur.hasConflict(c)) {
                cur.addCourse(c);
                dfs(groups, idx + 1, cur);
                cur.list.remove(cur.list.size() - 1);
                cur.totalCredit -= c.credit;
            }
        }

        dfs(groups, idx + 1, cur);
    }
    //ai활용 종료
        int calcScore(Schedule s) {
        int score = 50;
        boolean hasMon = false, hasFri = false, has1st = false;
        int majReq = 0, genReq = 0;
        for (int i = 0; i < s.list.size(); i++) {
            Course c = s.list.get(i);
            if (c.day.equals("월")) hasMon = true;
            if (c.day.equals("금")) hasFri = true;
            if (c.startMin <= 540) has1st = true;
            if (c.type.equals("전필")) majReq++;
            if (c.type.equals("교필")) genReq++;
        }
        for (int i = 0; i < strategies.size(); i++) {
            String strat = strategies.get(i);
            int weight = (strategies.size() - i) * 10;
            if (strat.equals("금공강 우선") && !hasFri) score += weight;
            if (strat.equals("월공강 우선") && !hasMon) score += weight;
            if (strat.equals("1교시 배척") && !has1st) score += weight;
            if (strat.equals("전필 우선")) score += majReq * weight / 5;
            if (strat.equals("교필 우선")) score += genReq * weight / 5;
            if (strat.equals("최대학점")) score += s.totalCredit * weight / 5;
        }
        if (!has1st) score += 5;
        score += s.totalCredit * 3;
        score += s.list.size() * 5;
        return score;
    }

    ArrayList<Schedule> getTop5() {
        for (int i = 0; i < results.size(); i++) {
            for (int j = i + 1; j < results.size(); j++) {
                Schedule a = results.get(i);
                Schedule b = results.get(j);
                boolean swap = false;
                if (b.score > a.score) swap = true;
                else if (b.score == a.score && b.list.size() > a.list.size()) swap = true;
                else if (b.score == a.score && b.list.size() == a.list.size() && b.totalCredit > a.totalCredit) swap = true;
                if (swap) { results.set(i, b); results.set(j, a); }
            }
        }

        ArrayList<Schedule> unique = new ArrayList<Schedule>();
        for (int i = 0; i < results.size(); i++) {
            Schedule s = results.get(i);
            boolean tooSimilar = false;
            for (int k = 0; k < unique.size(); k++) {
                if (similarSchedule(s, unique.get(k))) { tooSimilar = true; break; }
            }
            if (!tooSimilar) unique.add(s);
            if (unique.size() >= 5) break;
        }

        String[] labels = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < unique.size(); i++) unique.get(i).label = labels[i];
        return unique;
    }

        //ai활용 시작 - 두 시간표의 유사도를 퍼센트로 계산해서 중복 제거
    boolean similarSchedule(Schedule a, Schedule b) {
        if (a.list.size() == 0 || b.list.size() == 0) return false;
        int same = 0;
        for (int i = 0; i < a.list.size(); i++) {
            Course ca = a.list.get(i);
            for (int j = 0; j < b.list.size(); j++) {
                Course cb = b.list.get(j);
                if (ca.name.equals(cb.name) && ca.day.equals(cb.day) && ca.startMin == cb.startMin) {
                    same++;
                    break;
                }
            }
        }
        int maxSize = Math.max(a.list.size(), b.list.size());
        return (same * 10 / maxSize) >= 8;
    }
    //ai활용 종료
    
    ArrayList<Course> getAlternatives(Course c, ArrayList<Course> all) {
        ArrayList<Course> alts = new ArrayList<Course>();
        for (int i = 0; i < all.size(); i++) {
            Course o = all.get(i);
            if (!o.code.equals(c.code) && o.dept.equals(c.dept) && o.type.equals(c.type))
                alts.add(o);
            if (alts.size() >= 5) break;
        }
        return alts;
    }
}
//ai활용 시작 - xlsx파일을 ZIP+XML구조로 직접 파싱하는 부분, 자바 기본라이브러리만 사용
class XlsxReader {

    static ArrayList<String[]> read(File xlsxFile) throws Exception {
        ArrayList<String[]> rows = new ArrayList<String[]>();
        ZipFile zip = new ZipFile(xlsxFile);

        ArrayList<String> sharedStrings = new ArrayList<String>();
        ZipEntry ssEntry = zip.getEntry("xl/sharedStrings.xml");
        if (ssEntry != null) sharedStrings = parseSharedStrings(zip.getInputStream(ssEntry));

        ZipEntry sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml");
        if (sheetEntry == null) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.getName().startsWith("xl/worksheets/sheet") && e.getName().endsWith(".xml")) {
                    sheetEntry = e; break;
                }
            }
        }
        if (sheetEntry != null) rows = parseSheet(zip.getInputStream(sheetEntry), sharedStrings);
        zip.close();
        return rows;
    }

    static ArrayList<String> parseSharedStrings(InputStream is) throws Exception {
        ArrayList<String> list = new ArrayList<String>();
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        org.w3c.dom.Document doc = db.parse(is);
        org.w3c.dom.NodeList siNodes = doc.getElementsByTagName("si");
        for (int i = 0; i < siNodes.getLength(); i++) {
            org.w3c.dom.Element si = (org.w3c.dom.Element) siNodes.item(i);
            StringBuilder sb = new StringBuilder();
            org.w3c.dom.NodeList tNodes = si.getElementsByTagName("t");
            for (int j = 0; j < tNodes.getLength(); j++) sb.append(tNodes.item(j).getTextContent());
            list.add(sb.toString());
        }
        return list;
    }

    static ArrayList<String[]> parseSheet(InputStream is, ArrayList<String> ss) throws Exception {
        ArrayList<String[]> rows = new ArrayList<String[]>();
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        org.w3c.dom.Document doc = db.parse(is);
        org.w3c.dom.NodeList rowNodes = doc.getElementsByTagName("row");
        for (int i = 0; i < rowNodes.getLength(); i++) {
            org.w3c.dom.Element rowEl = (org.w3c.dom.Element) rowNodes.item(i);
            org.w3c.dom.NodeList cells = rowEl.getElementsByTagName("c");
            int maxCol = 0;
            for (int j = 0; j < cells.getLength(); j++) {
                int col = colIndex(((org.w3c.dom.Element) cells.item(j)).getAttribute("r"));
                if (col > maxCol) maxCol = col;
            }
            String[] row = new String[maxCol + 1];
            for (int k = 0; k <= maxCol; k++) row[k] = "";
            for (int j = 0; j < cells.getLength(); j++) {
                org.w3c.dom.Element cell = (org.w3c.dom.Element) cells.item(j);
                String t = cell.getAttribute("t");
                org.w3c.dom.NodeList vList = cell.getElementsByTagName("v");
                if (vList.getLength() == 0) continue;
                String val = vList.item(0).getTextContent();
                int col = colIndex(cell.getAttribute("r"));
                if (t.equals("s")) { try { val = ss.get(Integer.parseInt(val)); } catch (Exception e) {} }
                if (col < row.length) row[col] = val;
            }
            rows.add(row);
        }
        return rows;
    }

    static int colIndex(String ref) {
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') col = col * 26 + (c - 'A' + 1);
            else break;
        }
        return col - 1;
    }
}
//ai활용 종료
public class TimetableApp extends JFrame {

    ArrayList<Course> allCourses = new ArrayList<Course>();
    ArrayList<Course> filteredCourses = new ArrayList<Course>();
    ArrayList<Course> selectedCourses = new ArrayList<Course>();
    ArrayList<Boolean> mustFlags = new ArrayList<Boolean>();
    ArrayList<Schedule> top5 = new ArrayList<Schedule>();
    Engine engine = new Engine();

    JTextField searchField;
    JComboBox creditCombo;
    JList gradeList, deptList, typeList;
    DefaultListModel gradeModel, deptModel, typeModel;

    JList stratOrderList;
    DefaultListModel stratOrderModel;
    JButton stratUpBtn, stratDownBtn;

    JList courseListView, resultListView, altListView;
    DefaultListModel courseListModel, resultListModel, altListModel;

    JTable selectedTable;
    javax.swing.table.DefaultTableModel selectedTableModel;

    JCheckBox mondayOffCheck, fridayOffCheck;
    JSpinner maxCreditSpinner;
    JTable timetableTable;
    JTextArea detailArea;
    JButton generateBtn, addBtn, removeBtn, loadBtn;
    JLabel selectedLabel, fileLabel;

    public TimetableApp() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        setTitle("시간표 추천 시스템");
        setSize(1300, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(makeNorth(), BorderLayout.NORTH);
        add(makeCenter(), BorderLayout.CENTER);
        add(makeSouth(), BorderLayout.SOUTH);
        setVisible(true);
    }

    JPanel makeNorth() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(BorderFactory.createTitledBorder("파일 & 시간표 생성"));

        loadBtn = new JButton("엑셀 파일 불러오기 (.xlsx)");
        loadBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { loadExcel(); }
        });
        p.add(loadBtn);

        fileLabel = new JLabel("파일 미선택");
        fileLabel.setForeground(Color.GRAY);
        p.add(fileLabel);

        p.add(Box.createHorizontalStrut(30));

        generateBtn = new JButton("시간표 생성");
        generateBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { doGenerate(); }
        });
        p.add(generateBtn);

        selectedLabel = new JLabel("선택된 과목: 0개 / 0학점");
        p.add(selectedLabel);

        p.add(Box.createHorizontalStrut(20));
        p.add(new JLabel("최대 수강학점:"));
        maxCreditSpinner = new JSpinner(new SpinnerNumberModel(18, 1, 30, 1));
        maxCreditSpinner.setPreferredSize(new Dimension(55, 25));
        p.add(maxCreditSpinner);
        p.add(new JLabel("학점"));

        return p;
    }

    JSplitPane makeCenter() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setLeftComponent(makeLeftPanel());
        split.setRightComponent(makeRightPanel());
        split.setDividerLocation(500);
        return split;
    }

    JPanel makeLeftPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("과목 검색 및 선택"));

        JPanel filterPanel = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3,3,3,3);
        g.fill = GridBagConstraints.BOTH;

        g.gridx=0; g.gridy=0; g.weightx=0; g.weighty=0; g.fill=GridBagConstraints.HORIZONTAL;
        filterPanel.add(new JLabel("과목명 검색:"), g);
        g.gridx=1; g.weightx=1.0;
        searchField = new JTextField(12);
        filterPanel.add(searchField, g);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterCourses(); }
            public void removeUpdate(DocumentEvent e) { filterCourses(); }
            public void changedUpdate(DocumentEvent e) { filterCourses(); }
        });

        g.gridx=0; g.gridy=1; g.weightx=0; g.weighty=0;
        filterPanel.add(new JLabel("학년:"), g);
        g.gridx=1; g.weightx=1.0; g.weighty=0.3; g.fill=GridBagConstraints.BOTH;
        gradeModel = new DefaultListModel();
        for (String gr : new String[]{"1","2","3","4","5"}) gradeModel.addElement(gr + "학년");
        gradeList = new JList(gradeModel);
        gradeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        gradeList.setVisibleRowCount(3);
        gradeList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) { if (!e.getValueIsAdjusting()) filterCourses(); }
        });
        filterPanel.add(new JScrollPane(gradeList), g);

        g.gridx=0; g.gridy=2; g.weightx=0; g.weighty=0; g.fill=GridBagConstraints.HORIZONTAL;
        filterPanel.add(new JLabel("전공:"), g);
        g.gridx=1; g.weightx=1.0; g.weighty=0.5; g.fill=GridBagConstraints.BOTH;
        deptModel = new DefaultListModel();
        deptList = new JList(deptModel);
        deptList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        deptList.setVisibleRowCount(4);
        deptList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) { if (!e.getValueIsAdjusting()) filterCourses(); }
        });
        filterPanel.add(new JScrollPane(deptList), g);

        g.gridx=0; g.gridy=3; g.weightx=0; g.weighty=0; g.fill=GridBagConstraints.HORIZONTAL;
        filterPanel.add(new JLabel("구분:"), g);
        g.gridx=1; g.weightx=1.0; g.weighty=0.3; g.fill=GridBagConstraints.BOTH;
        typeModel = new DefaultListModel();
        for (String t : new String[]{"전필","전선","교필","교선"}) typeModel.addElement(t);
        typeList = new JList(typeModel);
        typeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        typeList.setVisibleRowCount(3);
        typeList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) { if (!e.getValueIsAdjusting()) filterCourses(); }
        });
        filterPanel.add(new JScrollPane(typeList), g);

        g.gridx=0; g.gridy=4; g.weightx=0; g.weighty=0; g.fill=GridBagConstraints.HORIZONTAL;
        filterPanel.add(new JLabel("학점:"), g);
        g.gridx=1; g.weightx=1.0;
        creditCombo = new JComboBox(new String[]{"전체","1","2","3","4","5","6"});
        filterPanel.add(creditCombo, g);
        creditCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { filterCourses(); }
        });

        p.add(filterPanel, BorderLayout.NORTH);

        courseListModel = new DefaultListModel();
        courseListView = new JList(courseListModel);
        courseListView.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane sp = new JScrollPane(courseListView);
        sp.setPreferredSize(new Dimension(480, 200));
        p.add(sp, BorderLayout.CENTER);

        JPanel btnp = new JPanel(new FlowLayout());
        addBtn = new JButton("추가 ▼");
        removeBtn = new JButton("제거 ▲");
        addBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { addCourse(); }
        });
        removeBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { removeCourse(); }
        });
        btnp.add(addBtn);
        btnp.add(removeBtn);
        p.add(btnp, BorderLayout.SOUTH);

        return p;
    }

    JPanel makeRightPanel() {
        JPanel p = new JPanel(new BorderLayout());

        JPanel selPanel = new JPanel(new BorderLayout());
        selPanel.setBorder(BorderFactory.createTitledBorder("선택된 과목 (★ 체크 = 필수 포함)"));

        String[] cols = {"★필수", "과목명", "학점", "구분", "시간"};
        selectedTableModel = new javax.swing.table.DefaultTableModel(cols, 0) {
            public Class getColumnClass(int col) { return col == 0 ? Boolean.class : String.class; }
            public boolean isCellEditable(int row, int col) { return col == 0; }
        };
        selectedTable = new JTable(selectedTableModel);
        selectedTable.getColumnModel().getColumn(0).setMaxWidth(45);
        selectedTable.getColumnModel().getColumn(2).setMaxWidth(45);
        selectedTable.getColumnModel().getColumn(3).setMaxWidth(45);
        JScrollPane selScroll = new JScrollPane(selectedTable);
        selScroll.setPreferredSize(new Dimension(400, 150));
        selPanel.add(selScroll, BorderLayout.CENTER);

        JPanel stratPanel = new JPanel(new BorderLayout());
        stratPanel.setBorder(BorderFactory.createTitledBorder("전략 우선순위 (위쪽이 1순위 / 드래그로 순서변경)"));

        JPanel dayOffPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        mondayOffCheck = new JCheckBox("월요일 공강");
        fridayOffCheck = new JCheckBox("금요일 공강");
        dayOffPanel.add(mondayOffCheck);
        dayOffPanel.add(fridayOffCheck);

        stratOrderModel = new DefaultListModel();
        for (String s : new String[]{"최대학점","1교시 배척","전필 우선","교필 우선"})
            stratOrderModel.addElement(s);
        stratOrderList = new JList(stratOrderModel);
        stratOrderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stratOrderList.setVisibleRowCount(3);

        JPanel stratBtns = new JPanel(new FlowLayout());
        stratUpBtn = new JButton("▲ 위로");
        stratDownBtn = new JButton("▼ 아래로");
        stratUpBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { moveStrat(-1); }
        });
        stratDownBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { moveStrat(1); }
        });
        stratBtns.add(stratUpBtn);
        stratBtns.add(stratDownBtn);

        stratPanel.add(dayOffPanel, BorderLayout.NORTH);
        stratPanel.add(new JScrollPane(stratOrderList), BorderLayout.CENTER);
        stratPanel.add(stratBtns, BorderLayout.SOUTH);

        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(BorderFactory.createTitledBorder("추천 결과 (클릭하면 하단에 시간표 표시)"));
        resultListModel = new DefaultListModel();
        resultListView = new JList(resultListModel);
        resultListView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultListView.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                showSchedule();
            }
        });
        resultPanel.add(new JScrollPane(resultListView), BorderLayout.CENTER);

        p.add(selPanel, BorderLayout.NORTH);
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, stratPanel, resultPanel);
        rightSplit.setDividerLocation(180);
        p.add(rightSplit, BorderLayout.CENTER);

        return p;
    }

    JPanel makeSouth() {
        JPanel p = new JPanel(new GridLayout(1, 2));
        p.setBorder(BorderFactory.createTitledBorder("시간표 상세"));
        p.setPreferredSize(new Dimension(1300, 280));

        String[] days = {"시간","월","화","수","목","금"};
        int[] slots = {540,600,660,720,780,840,900,960,1020,1080};
        String[] timeLabels = {"09:00","10:00","11:00","12:00","13:00","14:00","15:00","16:00","17:00","18:00"};

        timetableTable = new JTable(slots.length, days.length);
        timetableTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        for (int i = 0; i < days.length; i++)
            timetableTable.getColumnModel().getColumn(i).setHeaderValue(days[i]);
        for (int i = 0; i < timeLabels.length; i++)
            timetableTable.setValueAt(timeLabels[i], i, 0);

        timetableTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = timetableTable.rowAtPoint(e.getPoint());
                int col = timetableTable.columnAtPoint(e.getPoint());
                if (col > 0 && row >= 0) {
                    Object val = timetableTable.getValueAt(row, col);
                    if (val != null && !val.toString().isEmpty()) showCourseDetail(val.toString());
                }
            }
        });

        p.add(new JScrollPane(timetableTable));

        JPanel right = new JPanel(new BorderLayout());
        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        JScrollPane ds = new JScrollPane(detailArea);
        ds.setBorder(BorderFactory.createTitledBorder("과목 상세"));

        altListModel = new DefaultListModel();
        altListView = new JList(altListModel);
        JScrollPane as = new JScrollPane(altListView);
        as.setBorder(BorderFactory.createTitledBorder("대체 과목 추천"));
        as.setPreferredSize(new Dimension(300, 100));

        right.add(ds, BorderLayout.CENTER);
        right.add(as, BorderLayout.SOUTH);
        p.add(right);

        return p;
    }

    void moveStrat(int dir) {
        int idx = stratOrderList.getSelectedIndex();
        if (idx < 0) return;
        int newIdx = idx + dir;
        if (newIdx < 0 || newIdx >= stratOrderModel.size()) return;
        Object tmp = stratOrderModel.get(idx);
        stratOrderModel.set(idx, stratOrderModel.get(newIdx));
        stratOrderModel.set(newIdx, tmp);
        stratOrderList.setSelectedIndex(newIdx);
    }

    void loadExcel() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("엑셀 파일 선택");
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel 파일 (*.xlsx)", "xlsx"));
        fc.setCurrentDirectory(new File(System.getProperty("user.home")));
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); SwingUtilities.updateComponentTreeUI(fc); } catch (Exception ex) {}

        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();

        try {
            ArrayList<String[]> rows = XlsxReader.read(f);
            allCourses = parseRows(rows);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "파일 읽기 실패: " + e.getMessage());
            return;
        }

        if (allCourses.isEmpty()) {
            JOptionPane.showMessageDialog(this, "과목을 불러오지 못했습니다.");
            return;
        }

        TreeSet<String> depts = new TreeSet<String>();
        for (int i = 0; i < allCourses.size(); i++) depts.add(allCourses.get(i).dept);
        deptModel.clear();
        for (String d : depts) deptModel.addElement(d);

        fileLabel.setText(f.getName() + "  (" + allCourses.size() + "개 과목)");
        fileLabel.setForeground(new Color(0,128,0));

        selectedCourses.clear(); mustFlags.clear();
        selectedTableModel.setRowCount(0);
        resultListModel.clear(); top5.clear();
        updateSelectedLabel();
        filterCourses();
        JOptionPane.showMessageDialog(this, allCourses.size() + "개 과목을 불러왔습니다!");
    }

    ArrayList<Course> parseRows(ArrayList<String[]> rows) {
        ArrayList<Course> list = new ArrayList<Course>();
        Pattern timePat = Pattern.compile("([월화수목금토])[\\dA-Z\\-]+.*?\\((\\d+):(\\d+)-(\\d+):(\\d+)\\)");
        Pattern roomPat = Pattern.compile("\\)\\(([^)]+)\\)");

        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (r.length < 9) continue;
            String dept = r[0].trim();
            if (dept.isEmpty() || dept.equals("개설학과(전공)") || dept.startsWith("*") || dept.startsWith("2026")) continue;

            String gradeStr = r[1].trim();
            int grade = 0;
            try { grade = Integer.parseInt(gradeStr); } catch (Exception e) {}

            String ctype = r[2].trim();
            String code  = r[3].trim();
            String name  = r[4].trim();
            String prof  = r[7].trim();
            String timeroom = r[8].trim();

            int credit;
            try { credit = (int) Double.parseDouble(r[5].trim()); } catch (Exception e) { continue; }

            Matcher tm = timePat.matcher(timeroom);
            if (!tm.find()) continue;

            String day = tm.group(1);
            int sMin = Integer.parseInt(tm.group(2)) * 60 + Integer.parseInt(tm.group(3));
            int eMin = Integer.parseInt(tm.group(4)) * 60 + Integer.parseInt(tm.group(5));
            Matcher rm = roomPat.matcher(timeroom);
            String room = rm.find() ? rm.group(1) : "";

            list.add(new Course(dept, ctype, code, name, credit, prof, day, sMin, eMin, room, grade));
        }
        return list;
    }

    void filterCourses() {
        String keyword = searchField.getText().trim();
        int[] selGrades = gradeList.getSelectedIndices();
        int[] selTypes  = typeList.getSelectedIndices();
        java.util.List selDepts = deptList.getSelectedValuesList();
        String selCredit = (String) creditCombo.getSelectedItem();

        filteredCourses.clear();
        courseListModel.clear();

        for (int i = 0; i < allCourses.size(); i++) {
            Course c = allCourses.get(i);

            if (!keyword.isEmpty() && !c.name.contains(keyword)) continue;

            if (selGrades.length > 0) {
                boolean ok = false;
                for (int gi : selGrades) { if (c.grade == gi + 1) { ok = true; break; } }
                if (!ok) continue;
            }

            if (!selDepts.isEmpty() && !selDepts.contains(c.dept)) continue;

            if (selTypes.length > 0) {
                String[] typeArr = {"전필","전선","교필","교선"};
                boolean ok = false;
                for (int ti : selTypes) { if (c.type.equals(typeArr[ti])) { ok = true; break; } }
                if (!ok) continue;
            }

            if (!selCredit.equals("전체")) {
                try { if (c.credit != Integer.parseInt(selCredit)) continue; } catch (Exception ex) {}
            }

            filteredCourses.add(c);
            courseListModel.addElement(c.dept + " | " + c.grade + "학년 | " + c.type + " | " + c.name + "  " + c.credit + "학점  (" + c.prof + ")  " + c.getTimeStr());
        }
    }

    void addCourse() {
        int[] idxs = courseListView.getSelectedIndices();
        if (idxs.length == 0) { JOptionPane.showMessageDialog(this, "과목을 선택해주세요."); return; }
        int added = 0;
        for (int k = 0; k < idxs.length; k++) {
            Course c = filteredCourses.get(idxs[k]);
            boolean dup = false;
            for (int i = 0; i < selectedCourses.size(); i++) {
                if (selectedCourses.get(i).code.equals(c.code)) { dup = true; break; }
            }
            if (dup) continue;
            selectedCourses.add(c);
            mustFlags.add(false);
            selectedTableModel.addRow(new Object[]{false, c.name, c.credit + "학점", c.type, c.getTimeStr()});
            added++;
        }
        if (added == 0) JOptionPane.showMessageDialog(this, "선택한 과목이 이미 모두 추가되어 있습니다.");
        updateSelectedLabel();
    }

    void removeCourse() {
        int[] idxs = selectedTable.getSelectedRows();
        if (idxs.length == 0) { JOptionPane.showMessageDialog(this, "제거할 과목을 선택해주세요."); return; }
        for (int i = idxs.length - 1; i >= 0; i--) {
            selectedCourses.remove(idxs[i]);
            mustFlags.remove(idxs[i]);
            selectedTableModel.removeRow(idxs[i]);
        }
        updateSelectedLabel();
    }

    void updateSelectedLabel() {
        int total = 0;
        for (int i = 0; i < selectedCourses.size(); i++) total += selectedCourses.get(i).credit;
        selectedLabel.setText("선택된 과목: " + selectedCourses.size() + "개 / " + total + "학점");
    }

    void doGenerate() {
        if (allCourses.isEmpty()) { JOptionPane.showMessageDialog(this, "먼저 엑셀 파일을 불러와주세요."); return; }
        if (selectedCourses.isEmpty()) { JOptionPane.showMessageDialog(this, "과목을 먼저 선택해주세요."); return; }

        ArrayList<Course> mustList = new ArrayList<Course>();
        ArrayList<Course> optList  = new ArrayList<Course>();

        for (int i = 0; i < selectedCourses.size(); i++) {
            Boolean must = (Boolean) selectedTableModel.getValueAt(i, 0);
            if (must != null && must) mustList.add(selectedCourses.get(i));
            else optList.add(selectedCourses.get(i));
        }

        for (int i = 0; i < mustList.size(); i++) {
            for (int j = i + 1; j < mustList.size(); j++) {
                if (mustList.get(i).conflicts(mustList.get(j))) {
                    JOptionPane.showMessageDialog(this,
                        "필수 과목 충돌!\n[" + mustList.get(i).name + "] 와(과)\n[" + mustList.get(j).name + "] 의 시간이 겹칩니다.\n필수 체크를 조정해주세요.");
                    return;
                }
            }
        }

        int total = 0;
        for (int i = 0; i < selectedCourses.size(); i++) total += selectedCourses.get(i).credit;

        int maxCredit = (Integer) maxCreditSpinner.getValue();

        ArrayList<String> strats = new ArrayList<String>();
        if (mondayOffCheck.isSelected()) strats.add("월공강 우선");
        if (fridayOffCheck.isSelected()) strats.add("금공강 우선");
        for (int i = 0; i < stratOrderModel.size(); i++) strats.add((String) stratOrderModel.get(i));

        final int finalTarget = total;
        final int finalMaxCredit = maxCredit;
        final ArrayList<String> finalStrats = strats;
        final ArrayList<Course> finalMust = mustList;
        final ArrayList<Course> finalOpt  = optList;

        generateBtn.setEnabled(false);
        generateBtn.setText("생성 중...");

        new Thread(new Runnable() {
            public void run() {
                engine.generate(finalMust, finalOpt, finalTarget, finalMaxCredit, finalStrats);
                top5 = engine.getTop5();
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        resultListModel.clear();
                        if (top5.isEmpty()) {
                            JOptionPane.showMessageDialog(TimetableApp.this, "조건에 맞는 시간표가 없습니다.\n과목을 더 추가하거나 필수 체크를 줄여보세요.");
                        } else {
                            for (int i = 0; i < top5.size(); i++) {
                                Schedule s = top5.get(i);
                                resultListModel.addElement("시간표 " + s.label + "  |  " + s.totalCredit + "학점  |  점수: " + s.score + "점");
                            }
                            resultListView.setSelectedIndex(0);
                            showSchedule();
                        }
                        generateBtn.setEnabled(true);
                        generateBtn.setText("시간표 생성");
                    }
                });
            }
        }).start();
    }

    void showSchedule() {
        int idx = resultListView.getSelectedIndex();
        if (idx < 0 || idx >= top5.size()) return;
        Schedule s = top5.get(idx);

        for (int r = 0; r < timetableTable.getRowCount(); r++)
            for (int c = 1; c < timetableTable.getColumnCount(); c++)
                timetableTable.setValueAt("", r, c);

        String[] dayOrder = {"월","화","수","목","금"};
        int[] slots = {540,600,660,720,780,840,900,960,1020,1080};

        for (int i = 0; i < s.list.size(); i++) {
            Course c = s.list.get(i);
            int col = -1;
            for (int d = 0; d < dayOrder.length; d++) {
                if (dayOrder[d].equals(c.day)) { col = d + 1; break; }
            }
            if (col < 0) continue;
            for (int r = 0; r < slots.length; r++) {
                if (c.startMin <= slots[r] && slots[r] < c.endMin)
                    timetableTable.setValueAt(c.name, r, col);
            }
        }

        timetableTable.getTableHeader().repaint();
        timetableTable.repaint();

        detailArea.setText("[ 시간표 " + s.label + " ]  " + s.totalCredit + "학점  점수: " + s.score + "\n");
        detailArea.append("─────────────────────────────\n");
        for (int i = 0; i < s.list.size(); i++) {
            Course c = s.list.get(i);
            detailArea.append("[" + c.type + "] " + c.name + "  " + c.credit + "학점\n");
            detailArea.append("  " + c.prof + "  " + c.getTimeStr() + "  " + c.room + "\n\n");
        }
        detailArea.append("※ 시간표 칸 클릭시 과목 상세 및 대체과목 표시");
        altListModel.clear();
    }

    void showCourseDetail(String courseName) {
        Course found = null;
        for (int i = 0; i < allCourses.size(); i++) {
            if (allCourses.get(i).name.equals(courseName)) { found = allCourses.get(i); break; }
        }
        if (found == null) return;
        detailArea.setText("");
        detailArea.append("과목명: " + found.name + "\n");
        detailArea.append("교수: " + found.prof + "\n");
        detailArea.append("학점: " + found.credit + "\n");
        detailArea.append("구분: " + found.type + "\n");
        detailArea.append("강의실: " + found.room + "\n");
        detailArea.append("시간: " + found.getTimeStr() + "\n");

        altListModel.clear();
        ArrayList<Course> alts = engine.getAlternatives(found, allCourses);
        for (int i = 0; i < alts.size(); i++) {
            Course a = alts.get(i);
            altListModel.addElement(a.name + " (" + a.prof + ") " + a.getTimeStr());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { new TimetableApp(); }
        });
    }
}
