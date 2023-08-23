package myspringframe.utils;

import myspringframe.Utils.ClassPathUtils;
import myspringframe.Utils.YamlUtils;
import myspringframe.context.ApplicationContext;
import myspringframe.context.ApplicationContextUtils;
import myspringframe.io.propertyresolver.PropertyResolver;
import myspringframe.webmvc.DispatcherServlet;
import myspringframe.webmvc.FilterRegistrationBean;

import javax.servlet.*;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.*;

public class WebUtils {
    public static final String DEFAULT_PARAM_VALUE = "\0\t\0\t\0";

    static final String CONFIG_APP_YAML = "/application.yml";
    static final String CONFIG_APP_PROP = "/application.properties";

    public static PropertyResolver createPropertyResolver(){
        final Properties props = new Properties();
        try {
            Map<String, Object> ymlMap = YamlUtils.loadYamlAsPlainMap(CONFIG_APP_YAML);
            for (String key : ymlMap.keySet()){
                Object value = ymlMap.get(key);
                if (value instanceof String){
                    String strValue = (String) value;
                    props.put(key,strValue);
                }
            }
        }
        catch (UncheckedIOException e){
            if (e.getCause() instanceof FileNotFoundException) {
                // try load application.properties:
                ClassPathUtils.readInputStream(CONFIG_APP_PROP, (input) -> {
                    props.load(input);
                    return true;
                });
            }
        }
        return new PropertyResolver(props);
    }

    public static void registerDispatcherServlet(ServletContext servletContext,PropertyResolver propertyResolver){
        DispatcherServlet dispatcherServlet= new DispatcherServlet(ApplicationContextUtils.getApplicationContext(),propertyResolver);
        ServletRegistration.Dynamic dispatcherReg = servletContext.addServlet("dispatcherServlet",dispatcherServlet);
        dispatcherReg.addMapping("/");
        dispatcherReg.setLoadOnStartup(0);
    }

    public static void registerFilters(ServletContext servletContext) {
        ApplicationContext applicationContext = ApplicationContextUtils.getRequiredApplicationContext();
        for (FilterRegistrationBean filterRegBean : applicationContext.getBeans(FilterRegistrationBean.class)) {
            List<String> urlPatterns = filterRegBean.getUrlPatterns();
            if (urlPatterns == null || urlPatterns.isEmpty()) {
                throw new IllegalArgumentException("No url patterns for {}" + filterRegBean.getClass().getName());
            }
            Filter filter = Objects.requireNonNull(filterRegBean.getFilter(), "FilterRegistrationBean.getFilter() must not return null.");
            FilterRegistration.Dynamic  filterReg = servletContext.addFilter(filterRegBean.getName(), filter);
            filterReg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, urlPatterns.toArray(new String[urlPatterns.size()]));
        }
    }

}
