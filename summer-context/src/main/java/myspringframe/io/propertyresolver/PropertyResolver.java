package myspringframe.io.propertyresolver;

import java.time.*;
import java.util.*;
import java.util.function.Function;

public class PropertyResolver {
    Map<String,String> properties=new HashMap<>();
    Map<Class<?>, Function<String, Object>> converters = new HashMap<>();
    public PropertyResolver(Properties props){
        properties.putAll(System.getenv());
        Set<String> names = props.stringPropertyNames();
        for (String name : names){
            properties.put(name,props.getProperty(name));
        }
        converters.put(String.class, s -> s);
        converters.put(boolean.class, s -> Boolean.parseBoolean(s));
        converters.put(Boolean.class, s -> Boolean.valueOf(s));

        converters.put(byte.class, s -> Byte.parseByte(s));
        converters.put(Byte.class, s -> Byte.valueOf(s));

        converters.put(short.class, s -> Short.parseShort(s));
        converters.put(Short.class, s -> Short.valueOf(s));

        converters.put(int.class, s -> Integer.parseInt(s));
        converters.put(Integer.class, s -> Integer.valueOf(s));

        converters.put(long.class, s -> Long.parseLong(s));
        converters.put(Long.class, s -> Long.valueOf(s));

        converters.put(float.class, s -> Float.parseFloat(s));
        converters.put(Float.class, s -> Float.valueOf(s));

        converters.put(double.class, s -> Double.parseDouble(s));
        converters.put(Double.class, s -> Double.valueOf(s));

        converters.put(LocalDate.class, s -> LocalDate.parse(s));
        converters.put(LocalTime.class, s -> LocalTime.parse(s));
        converters.put(LocalDateTime.class, s -> LocalDateTime.parse(s));
        converters.put(ZonedDateTime.class, s -> ZonedDateTime.parse(s));
        converters.put(Duration.class, s -> Duration.parse(s));
        converters.put(ZoneId.class, s -> ZoneId.of(s));
    }


    public String getProperty(String key){
        PropertyExpr expr = parsePropertyExpr(key);
        if (expr != null) {
            if (expr.defaultvalue != null) {
                return getProperty(expr.key,expr.defaultvalue);
            }
            else {
                return getProperty(expr.key);
            }
        }
        String value = this.properties.get(key);
        if (value != null) return parseValue(value);
        return value;
    }


    public String getProperty(String key,String defaultValue){
        if (defaultValue == null) return getProperty(key);
        String value = getProperty(key);
        String dValue = parseValue(defaultValue);
        if (value == null) return dValue;
        else return value;
    }


    public <T> T getProperty(String key, Class<T> targetType) {
        String value = getProperty(key);
        if (value == null) {
            return null;
        }
        return convert(targetType, value);
    }

    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return convert(targetType, value);
    }

    public <T> T convert(Class<?> targetType, String value){
        Function<String, Object> fn = this.converters.get(targetType);
        if (fn == null) {
            throw new IllegalArgumentException("Unsupported value type: " + targetType.getName());
        }
        return (T) fn.apply(value);
    }


    PropertyExpr parsePropertyExpr(String key){
        if (key.startsWith("${")&&key.endsWith("}")){
            int idx = key.indexOf(':');
            if (idx == -1) {
                key = key.substring(2,key.length()-1);
                return new PropertyExpr(key,null);
            }
            else {
                String newkey = key.substring(2,idx);
                String defaultvalue=key.substring(idx+1,key.length()-1);
                return new PropertyExpr(newkey, defaultvalue);
            }
        }
        return null;
    }

    public String parseValue(String value){
        PropertyExpr expr = parsePropertyExpr(value);
        if (expr == null) return value;
        if (expr.defaultvalue != null) {
            return getProperty(expr.key,expr.defaultvalue);
        }
        else {
            return getProperty(expr.key);
        }
    }

    String notEmpty(String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
        return key;
    }

    public <T> T getRequiredProperty(String key, Class<T> targetType){
        T value = getProperty(key,targetType);
        return Objects.requireNonNull(value, "Property '" + key + "' not found.");
    }



}
