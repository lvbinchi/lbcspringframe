package myspringframe.Utils;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class YamlUtils {
    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadYaml(String path) {

        Yaml yaml =
                new Yaml(new Constructor(), new Representer(), new DumperOptions(), new LoaderOptions(), new NoImplicitResolver());
        return ClassPathUtils.readInputStream(path, (input) -> {
            return (Map<String, Object>) yaml.load(input);
        });
    }
    public static Map<String, Object> loadYamlAsPlainMap(String path){
        Map<String,Object> source = loadYaml(path);
        Map<String, Object> plain=new LinkedHashMap<>();
        convertTo(source,"",plain);
        return plain;
    }
    static void convertTo(Map<String, Object> source, String prefix, Map<String, Object> plain){
        for (String k : source.keySet()){
            Object value = source.get(k);
            if (value instanceof Map){
                Map<String, Object> subMap = (Map<String, Object>) value;
                convertTo(subMap,prefix + k + ".",plain);
            }
            else if (value instanceof List){
                plain.put(prefix + k,value);
            }
            else plain.put(prefix + k, value.toString());
        }
    }
}

class NoImplicitResolver extends Resolver {
    public NoImplicitResolver() {
        super();
        super.yamlImplicitResolvers.clear();
    }
}
