package ru.dsi.bgbilling.modules.phone.antifraud;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import org.apache.log4j.Logger;
import ru.bitel.bgbilling.common.BGException;
import ru.bitel.bgbilling.kernel.contract.api.common.bean.Contract;
import ru.bitel.bgbilling.kernel.contract.api.server.bean.ContractDao;
import ru.bitel.bgbilling.kernel.script.server.dev.GlobalScriptBase;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.common.sql.ConnectionSet;

/**
 *
 * @author
 */
public class UnlockUser extends GlobalScriptBase {

    @Override
    public void execute(Setup setup, ConnectionSet connectionSet) throws Exception {

        // обработчик ошибок
        Logger logger = Logger.getLogger(this.getClass());

        // определение текущего времени
        Calendar now = Calendar.getInstance();

        Calendar lastDay = (Calendar) now.clone();
        lastDay.add(Calendar.DAY_OF_MONTH, -1);

        // подключение к БД
        Connection con = null;
        try {
            con = connectionSet.getConnection();
        } catch (Exception ex) {
            //logger.error("Не удалось подключиться к БД\n");
            //logger.error(ex.getMessage(), ex);
            throw new BGException("Ошибка подключения к БД в скрипте UnlockUser\n" + ex.getMessage() + "\n" + ex);
        }

        ContractDao cd = new ContractDao(con, 0);
        Contract c = null;

        con.setAutoCommit(false);

        try {
            String query = "Select `id`, `fc`, `cid` \n"
                    + "FROM lockabonent \n"
                    + "Where `fc`=0";
            PreparedStatement ps = con.prepareStatement(query);
            ResultSet rs = ps.executeQuery();
            try {
                while (rs.next()) {
                    c = cd.get(rs.getInt("cid"));
                    c.setStatus((byte) 0);
                    cd.update(c);

                    query = "DELETE FROM lockabonent  WHERE `fc`=" + rs.getInt("fc");
                    ps = con.prepareStatement(query);
                    ps.executeUpdate();
                }

                con.commit();

            } catch (SQLException ex) {
                //logger.error("Не удалось снять блокировку с абонента  = " + rs.getInt("cid") + "\n");
                //logger.error(ex.getMessage(), ex);
                throw new BGException("Не удалось снять блокировку с абонента  = " + rs.getInt("cid") + "\n" + ex.getMessage() + "\n" + ex);
            } finally {
                rs.close();
                ps.close();
            }

        } catch (SQLException ex) {
            //logger.error("Не удалось извлечь данные о заблокированных лицах\n");
            //logger.error(ex.getMessage(), ex);
            throw new BGException("Ошибка извлечения данных о заблокировнных абонентов\n" + ex.getMessage() + "\n" + ex);
        }

    }

}