package antifroud;

/**
 * Абонент - юридическое лицо
 *
 * @author
 */
public class LegalUser {

    /**
     * Номер договора
     */
    private int contract;

    /**
     * Статус договоора: 0 - подключен, 1 - на отключении, 2 - отключен, 3 -
     * закрыт, 4 - приостановлен, 5 - на подключении
     */
    private int status;

    LegalUser(int contract, int status) {
        this.contract = contract;
        this.status = status;
    }

    /**
     * Получение номера договора
     *
     * @return
     */
    public int getContractId() {
        return contract;
    }

    /** 
     * Получение статуса договора
     * @return 
     */
    public int getStatus() {
        return status;
    }

    /**
     * Установить статус договра
     * @param status статус договора
     */
    public void setStatus(int status) {
        this.status = status;
    }

}
