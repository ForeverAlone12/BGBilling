package ru.dsi.bgbilling.modules.phone.antifraud;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.log4j.Logger;
import ru.bitel.bgbilling.common.BGException;
import ru.bitel.bgbilling.kernel.contract.api.common.bean.Contract;
import ru.bitel.bgbilling.kernel.contract.api.server.bean.ContractDao;
import ru.bitel.bgbilling.kernel.script.server.dev.GlobalScriptBase;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.common.sql.ConnectionSet;

/**
 * Выключение телефонов абонентов-юридических лиц. Запускается в 00:00 каждый
 * день.
 *
 * @author
 */
public class LockLegalUsers extends GlobalScriptBase {

    @Override
    public void execute(Setup setup, ConnectionSet connectionSet) throws Exception {

        // обработчик ошибок
        Logger logger = Logger.getLogger(this.getClass());

        // подключение к БД
        Connection con = null;
        try {
            con = connectionSet.getConnection();
        } catch (Exception ex) {
            //logger.error("Не удалось подключиться к БД\n");
            //logger.error(ex.getMessage(), ex);
            throw new BGException("Ошибка подключения к БД в скрипте LockLegalUser\n" + ex.getMessage() + "\n" + ex);
        }

        int lock = 0; // количество заблокированных абонентов
        try {
            String query = "Select c.`id`, c.`fc` \n"
                    + "FROM contract c \n"
                    + "LEFT JOIN contract_module cm on c.`id`=cm.`cid` \n"
                    + "Where c.`fc`=1 \n"
                    + "AND c.`sub_mode`=0 \n"
                    + "AND cm.`mid`=6 \n"
                    + "AND c.`status`=0 \n"
                    + "AND c.`id` NOT IN (SELECT `id` FROM exception)\n";
            PreparedStatement ps = con.prepareStatement(query);
            ResultSet rs = ps.executeQuery();

            ContractDao cd = new ContractDao(con, 0);
            Contract c = null;

            con.setAutoCommit(false);
            
            while (rs.next()) {
                // блокировка юридических лиц
                c = cd.get(rs.getInt("id"));
                c.setStatus((byte) 4);
                cd.update(c);

                try {
                    // занесение в таблицу lockabonent заблокированных абонентов
                    query = "INSERT INTO lockabonent (`id`, `fc`, `cid`) \n"
                            + " VALUES ('', ?, ?)";
                    ps = con.prepareStatement(query);
                    ps.setInt(1, 1);
                    ps.setInt(2, rs.getInt("id"));
                    ps.executeUpdate();
                    lock++;

                } catch (SQLException ex) {
                    //logger.error("Не удалось внести данные о заблокированных абонентах\n");
                    //logger.error(ex.getMessage(), ex);
                    throw new BGException("Не удалось внести данные о заблокированных абонентах\n" + ex.getMessage() + "\n" + ex);
                }
            }

            con.commit();
            rs.close();
            ps.close();

        } catch (SQLException ex) {
            //logger.error("Не удалось извлечь данные о юридических лицах\n");
            //logger.error(ex.getMessage(), ex);
            throw new BGException("Ошибка выборки юридического лица для блокировки\n" + ex.getMessage() + "\n" + ex);
        }
        print("Количество заблокированных юр лиц на ночь = " + lock);
    }

}
