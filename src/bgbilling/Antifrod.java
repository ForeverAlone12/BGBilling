package bgbilling;

import java.util.Date;
import java.util.TimerTask;
import java.sql.*;

/**
 *
 * @author
 */
public class Antifrod extends TimerTask {

    /**
     * Таблица, где хранятся данные звонков
     */
    private static final String TABLE = "`log_session_18_201708_fraud`";

    /**
     * Код обслуживающего региона
     */
    private static final String CODE = "395";

    @Override
    public void run() {
        Date now = new Date();
        String date = String.format("%tF", now) + " "; // текущая дата

        long time = now.getTime(); //  определение времени запуска
        String thisHour = String.format("%tT", new Date(time));

        time -= (1000 * 60 * 60); // текущее время - (минус) 1 час
        String lastHour = String.format("%tT", new Date(time));

        // запрос на выборку данных за последний час
        String query = "SELECT *"
                + "FROM " + TABLE + ""
                + "WHERE  `session_start` <=" + thisHour + " AND `session_start`>" + lastHour;

        try {
            ResultSet rez = GetResult(query);

            while (rez.next()) {
                int numberDogobor = rez.getInt("cid");
                String fromNumber = rez.getString("from_number_164");
                String toNumber = rez.getString("to_number_164");

            }
        } catch (SQLException ex) {
            System.out.println("Ошибка составления запроса");
        }
    }

    /**
     * Подключение к БД
     *
     *
     * @return null - соединение не удалось, иначе - соединение установлено.
     */
    private static Connection DBConnect() throws SQLDataException {
        Connection dbConnect = null;
        String dbDriver = "com.mysql.jdbc.Driver";
        String dbUrl = "localhost";
        String dbUser = "";
        String dbPassw = "";

        try {
            Class.forName(dbDriver);
            dbConnect = DriverManager.getConnection(dbUrl, dbUser, dbPassw);
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Ошибка подключения к БД!");
        }
        return dbConnect;
    }

    /**
     * Получение данных из БД
     *
     * @param query запрос
     */
    private ResultSet GetResult(String query) throws SQLException {
        Connection con = null;
        Statement stmt = null;

        con = DBConnect();
        stmt = con.createStatement();
        ResultSet rez = stmt.executeQuery(query);
        
//        if (stmt != null) {
//            stmt.close();
//        }
//        if (con != null) {
//            con.close();
//        }
        return rez;

    }

    /**
     * Превышен ли лимит по внутризоновым телефонным соединениям для физических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean InZoneNatural(int session) {

        // количество доступных минут в день для физических лиц
        int LIMIT_MINUTES = 200;
        int LIMIT_SECONDS = LIMIT_MINUTES * 60;

        boolean rez = (session > LIMIT_SECONDS);

        return rez;
    }

    /**
     * Превышен ли лимит по внутризоновым телефонным соединениям для юридических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean InZoneLegal(int session) {
        // количество доступных минут в день для юридических лиц
        int LIMIT_MINUTES = 1000;
        int LIMIT_SECONDS = LIMIT_MINUTES * 60;

        boolean rez = (session > LIMIT_SECONDS);

        return rez;
    }

    /**
     * Превышен ли лимит по межгородним телефонным соединениям для физических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean IntercityNatural(int session) {
        // количество доступных минут в день для физических лиц
        int LIMIT_MINUTES = 200;
        int LIMIT_SECONDS = LIMIT_MINUTES * 60;

        boolean rez = (session > LIMIT_SECONDS);

        return rez;
    }

    /**
     * Превышен ли лимит по межгородним телефонным соединениям для юридических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean IntercityLegal(int session) {
        // количество доступных минут в день для физических лиц
        int LIMIT_MINUTES = 1000;
        int LIMIT_SECONDS = LIMIT_MINUTES * 60;

        boolean rez = (session > LIMIT_SECONDS);

        return rez;
    }

    /**
     * Превышен ли лимит по международным телефонным соединениям
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean International(int session) {
        // количество доступных минут в день
        int LIMIT_MINUTES = 120;
        int LIMIT_SECONDS = LIMIT_MINUTES * 60;

        boolean rez = (session > LIMIT_SECONDS);

        return rez;
    }

    /**
     * Определение типа исходящего звонка
     *
     * @param number телефонный номер в формате E.164
     * @return 1 - международный, 2 - междугородний, 3 - внутризоновый
     */
    public static int WhatTheCall(String number) {
        int call = 0;

        /* определение масштаба звонка:
            7 - звонок внутри страны, 
            8 - звонок в другие страны.
         */
        String codeCountry = number.substring(0, 1);
        if (!codeCountry.equals("7")) {
            call = 1;
        } else {
            String prefix = number.substring(1, 4);
            String numb = number.substring(4);
            call = prefix.equals(CODE) || Mobile.isHaveNumber(prefix, numb) ? 3 : 2;
        }

        return call;

    }
}
