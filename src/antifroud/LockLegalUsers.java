package antifroud;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import org.apache.log4j.Logger;
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
            logger.error("Не удалось подключиться к БД\n");
            logger.error(ex.getMessage(), ex);
        }

        // Выборка юридических лиц
        ArrayList<LegalUser> legalUser = new ArrayList<>();
        try {
            String query = "Select id, fc \n"
                    + "FROM contract c \n"
                    + "LEFT JOIN contract_module cm on c.id = cm.cid \n"
                    + "Where c.fc = 1 \n"
                    + "AND c.sub_mode = 0 \n"
                    + "AND cm.`mid` = 6 \n"
                    + "AND c.`status` = 0 \n"
                    + "AND c.id NOT IN (SELECT id FROM exception)\n";
            PreparedStatement ps = con.prepareStatement(query);
            ResultSet rs = ps.executeQuery();

            ContractDao cd;
            Contract c;
            while (rs.next()) {
                int contract = rs.getInt("id");
                // блокировка юридических лиц
                cd = new ContractDao(connectionSet.getConnection(), 0);
                c = cd.get(contract);
                c.setStatus((byte) 4);
                cd.update(c);

                try {
                    // занесение в таблицу lockabonent заблокированных абонентов
                    query = "INSERT INTO lockabonent (`id`, `fc`, `cid`) \n"
                            + " VALUES ('', ?, ?)";
                    ps = con.prepareStatement(query);
                    ps.setInt(1, 1);
                    ps.setInt(2, contract);
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    logger.error("Не удалось внести данные о заблокированных абонентов-юридических лиц\n");
                    logger.error(ex.getMessage(), ex);
                }
            }
        } catch (SQLException ex) {
            logger.error("Не удалось извлечь данные о юридических лицах\n");
            logger.error(ex.getMessage(), ex);
        }
    }

}
