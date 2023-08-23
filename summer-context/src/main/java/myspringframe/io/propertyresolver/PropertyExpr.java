package myspringframe.io.propertyresolver;

public class PropertyExpr {
    String key;
    String defaultvalue;

    public PropertyExpr(String key,String defaultvalue) {
        this.key = key;
        this.defaultvalue = defaultvalue;
    }

    public void setDefaultvalue(String defaultvalue) {
        this.defaultvalue = defaultvalue;
    }

    public String getDefaultvalue() {
        return defaultvalue;
    }
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }


}
