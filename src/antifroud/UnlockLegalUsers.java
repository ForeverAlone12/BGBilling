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
 * Включение телефонов абонентов-юридических лиц
 *
 * @author
 */
public class UnlockLegalUsers extends GlobalScriptBase {

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
                    + "FROM contract \n"
                    + "Where fc = 1 AND status = 4";
            PreparedStatement ps = con.prepareStatement(query);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int contract = rs.getInt("id");
                int status = rs.getInt("status");
                legalUser.add(new LegalUser(contract, status));
            }
        } catch (SQLException ex) {
            logger.error("Не удалось извлечь данные о юридических лицах\n");
            logger.error(ex.getMessage(), ex);
        }

        // Блокировка юридических лиц
        for (LegalUser user : legalUser) {
            ContractDao cd = new ContractDao(connectionSet.getConnection(), 0);
            Contract c = cd.get(user.getContractId());
            c.setStatus((byte) 0);
            cd.update(c);
            user.setStatus(0);
        }
    }

}
