package ru.dsi.bgbilling.modules.phone.antifraud;

/**
 *
 * @author
 */
public class ParamValue {

    /**
     * Номер договора
     */
    private int cid;

    /**
     * Название параметра
     */
    private String title;

    /**
     * Значение параметра
     */
    private int value;

    ParamValue(int cid, String title, int val) {
        this.cid = cid;
        this.title = title;
        this.value = val;
    }

    /**
     * @return the cid
     */
    public int getCid() {
        return cid;
    }

    /**
     * @param cid the cid to set
     */
    public void setCid(int cid) {
        this.cid = cid;
    }

    /**
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * @param title the title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * @return the value
     */
    public int getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(int value) {
        this.value = value;
    }

}
