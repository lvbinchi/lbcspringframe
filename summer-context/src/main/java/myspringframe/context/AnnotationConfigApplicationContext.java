package myspringframe.context;

import myspringframe.Utils.ClassUtils;
import myspringframe.annotation.*;
import myspringframe.exception.*;
import myspringframe.io.propertyresolver.PropertyResolver;
import myspringframe.io.resourcescan.ResourceResolver;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

public class AnnotationConfigApplicationContext implements ConfigurableApplicationContext{
    private Map<String, BeanDefinition> beans;
    protected final PropertyResolver propertyResolver;
    private List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    private Set<String> creatingBeanNames;

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver)
            throws IOException , URISyntaxException{
        ApplicationContextUtils.setApplicationContext(this);
        this.propertyResolver = propertyResolver;
        Set<String> beanClassNames=scanForClassNames(configClass);
        this.beans = creatBeanDefinitions(beanClassNames);
        this.creatingBeanNames = new HashSet<>();
        this.beans.values().stream().filter(this::isConfigurationDefinition).sorted().map(def->{
            createBeanAsEarlySingleton(def);
            return def.getName();
        }).collect(Collectors.toList());

        List<BeanPostProcessor> processors = this.beans.values().stream().filter(this::isBeanPostProcessorDefinition).sorted().
        map(def -> {
            return (BeanPostProcessor) createBeanAsEarlySingleton(def);
        }).collect(Collectors.toList());
        this.beanPostProcessors.addAll(processors);
        creatNormalBeans();
        this.beans.values().forEach(def -> {
            injectBean(def);
        });

        // 调用init方法:
        this.beans.values().forEach(def -> {
            initBean(def);
        });
    }

    void injectBean(BeanDefinition def){
        Object beanInstance = getProxiedInstance(def);
        try {
            injectProperties(def,def.getBeanClass(),beanInstance);
        }catch (ReflectiveOperationException e) {
            throw new BeanCreationException(e);
        }
    }

    void initBean(BeanDefinition def){
        callMethod(def.getInstance(), def.getInitMethod(), def.getInitMethodName());

    }
    void injectProperties(BeanDefinition def, Class<?> clazz, Object bean) throws ReflectiveOperationException{
        for (Field f : clazz.getDeclaredFields()){
            tryInjectProperties(def,clazz,bean,f);
        }
        for (Method m : clazz.getDeclaredMethods()){
            tryInjectProperties(def,clazz,bean,m);
        }
        Class<?> superClazz = clazz.getSuperclass();
        if (superClazz != null) {
            injectProperties(def, superClazz, bean);
        }
    }
    void tryInjectProperties(BeanDefinition def, Class<?> clazz, Object bean, AccessibleObject acc) throws ReflectiveOperationException{
        Value value = acc.getAnnotation(Value.class);
        Autowired autowired = acc.getAnnotation(Autowired.class);
        if (value == null && autowired == null) {
            return;
        }
        Field field = null;
        Method method = null;
        if (acc instanceof Field) {
            Field f = (Field) acc;
            checkFieldOrMethod(f);
            f.setAccessible(true);
            field = f;
        }
        if (acc instanceof Method) {
            Method m = (Method) acc;
            checkFieldOrMethod(m);
            if (m.getParameters().length != 1) {
                throw new BeanDefinitionException(
                        String.format("Cannot inject a non-setter method %s for bean '%s': %s", m.getName(), def.getName(), def.getBeanClass().getName()));
            }
            m.setAccessible(true);
            method = m;
        }
        String accessibleName = field != null ? field.getName() : method.getName();
        Class<?> accessibleType = field != null ? field.getType() : method.getParameterTypes()[0];
        if (value != null && autowired != null) {
            throw new BeanCreationException(String.format("Cannot specify both @Autowired and @Value when inject %s.%s for bean '%s': %s",
                    clazz.getSimpleName(), accessibleName, def.getName(), def.getBeanClass().getName()));
        }
        if (value != null) {
            Object propValue = this.propertyResolver.getRequiredProperty(value.value(), accessibleType);
            if (method != null) {
                method.invoke(bean,propValue);
            }
            if (field != null){
                field.set(bean,propValue);
            }
        }
        if (autowired != null){
            String name = autowired.name();
            boolean required = autowired.value();
            Object dependsOndef = name.isEmpty() ? findBean(accessibleType) : findBean(name, accessibleType);
            if (required && dependsOndef == null){
                throw new BeanCreationException(String.format("Missing autowired bean with type '%s' when create bean '%s': %s.", accessibleType.getName(),
                        def.getName(), def.getBeanClass().getName()));
            }
            if (dependsOndef != null) {
                if (field != null){
                    field.set(bean,dependsOndef);
                }
                if (method != null){
                    method.invoke(bean,dependsOndef);
                }
            }

        }
    }
    void checkFieldOrMethod(Member m) {
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)) {
            throw new BeanDefinitionException("Cannot inject static field: " + m);
        }
        if (Modifier.isFinal(mod)) {
            if (m instanceof Field) {
                throw new BeanDefinitionException("Cannot inject final field: " + m);
            }
        }
    }
    public Object createBeanAsEarlySingleton(BeanDefinition def){
        if (!this.creatingBeanNames.add(def.getName())){
            throw new UnsatisfiedDependencyException(String.format("Circular dependency detected when create bean '%s'", def.getName()));
        }
        Executable createFn =null;
        if (def.getFactoryName() == null){
            createFn = def.getConstructor();
        }
        else {
            createFn = def.getFactoryMethod();
        }
        final Parameter[] parameters = createFn.getParameters();
        final Annotation[][] parametersAnnos = createFn.getParameterAnnotations();
        Object[] args = new Object[parameters.length];
        for (int i = 0;i < parameters.length; i++){
            final Parameter param = parameters[i];
            final Annotation[] paraAnnos = parametersAnnos[i];
            final Value value = ClassUtils.getAnnotation(paraAnnos,Value.class);
            final Autowired autowired = ClassUtils.getAnnotation(paraAnnos,Autowired.class);

            final boolean isconfiguration = isConfigurationDefinition(def);
            if (isconfiguration && autowired != null){
                throw new BeanCreationException(
                        String.format("Cannot specify @Autowired when create @Configuration bean '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }
            if (value != null && autowired != null){
                throw new BeanCreationException(
                        String.format("Cannot specify both @Autowired and @Value when create bean '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }
            if (value == null && autowired == null) {
                throw new BeanCreationException(
                        String.format("Must specify @Autowired or @Value when create bean '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }
            final Class<?> type = param.getType();
            if (value != null) {
                args[i] = propertyResolver.getRequiredProperty(value.value(), type);
            }
            else {
                String name = autowired.name();
                boolean required = autowired.value();
                BeanDefinition dependsOnDef = name.isEmpty()?findBeanDefinition(type):findBeanDefinition(name,type);
                if (required && dependsOnDef == null){
                    throw new BeanCreationException(String.format("Missing autowired bean with type '%s' when create bean '%s': %s.", type.getName(),
                            def.getName(), def.getBeanClass().getName()));
                }
                if (dependsOnDef != null)  {
                    Object autowiredBeanInstance = dependsOnDef.getInstance();
                    if (autowiredBeanInstance == null){
                        autowiredBeanInstance = createBeanAsEarlySingleton(dependsOnDef);
                    }
                    args[i]=autowiredBeanInstance;
                }
                else {
                    args[i] = null;
                }
            }
        }
        Object instance = null;
        if (def.getFactoryName() == null) {
            try{
                instance = def.getConstructor().newInstance(args);
            }
            catch (Exception e){
                throw new BeanCreationException(String.format("Exception when create bean '%s': %s", def.getName(), def.getBeanClass().getName()), e);
            }
        }
        else {
            Object configInstance = getBean(def.getFactoryName());
            try{
                instance = def.getFactoryMethod().invoke(configInstance,args);
            }
            catch (Exception e){
                throw new BeanCreationException(String.format("Exception when create bean '%s': %s", def.getName(), def.getBeanClass().getName()), e);
            }
        }
        def.setInstance(instance);
        for (BeanPostProcessor processor : beanPostProcessors){
            Object processed = processor.postProcessBeforeInitialization(def.getInstance(), def.getName());
            if (def.getInstance() != processed) {
                def.setInstance(processed);
            }
        }
        return def.getInstance();
    }

    void creatNormalBeans(){
        List<BeanDefinition> defs = this.beans.values().stream().filter(def ->{
            return def.getInstance() == null;
        }).sorted().collect(Collectors.toList());
        defs.forEach(def -> {
            if (def.getInstance() == null) {
                createBeanAsEarlySingleton(def);
            }
        });
    }

    public Map<String, BeanDefinition> creatBeanDefinitions(Set<String> classNameSet){
        Map<String, BeanDefinition> defs = new HashMap<>();
        for (String className : classNameSet){
            Class<?> clazz = null;
            try{
                clazz = Class.forName(className);
            }catch(ClassNotFoundException e) {
                throw new BeanCreationException(e);
            }
            if (clazz.isAnnotation() || clazz.isEnum() || clazz.isInterface()) {
                continue;
            }
            Component component = ClassUtils.findAnnotation(clazz,Component.class);
            if (component != null) {
                String beanName = ClassUtils.getBeanName(clazz);
                BeanDefinition def = new BeanDefinition
                        (beanName, clazz, getSuitableConstructor(clazz), getOrder(clazz), clazz.isAnnotationPresent(Primary.class),
                                null,null,
                                ClassUtils.findAnnotationMethod(clazz, PostConstruct.class),
                                // destroy method:
                                ClassUtils.findAnnotationMethod(clazz, PreDestroy.class));
                addBeanDefinitions(defs, def);
                Configuration configuration = ClassUtils.findAnnotation(clazz,Configuration.class);
                if (configuration != null) {
                    scanFactoryMethods(beanName, clazz, defs);
                }
            }
        }
        return defs;
    }

    public void scanFactoryMethods(String beanName,Class<?> clazz, Map<String, BeanDefinition> defs){
        for (Method method : clazz.getDeclaredMethods()){
            Bean bean = method.getAnnotation(Bean.class);
            if (bean != null) {
                int mod = method.getModifiers();
                if (Modifier.isAbstract(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be abstract.");
                }
                if (Modifier.isFinal(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be final.");
                }
                if (Modifier.isPrivate(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be private.");
                }
                Class<?> beanClass = method.getReturnType();
                if (beanClass.isPrimitive()) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return primitive type.");
                }
                if (beanClass == void.class || beanClass == Void.class) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return void.");
                }
                BeanDefinition def = new BeanDefinition(ClassUtils.getBeanName(method),beanClass,beanName,method,getOrder(method),
                        method.isAnnotationPresent(Primary.class),
                        // init method:
                        bean.initMethod().isEmpty() ? null : bean.initMethod(),
                        // destroy method:
                        bean.destroyMethod().isEmpty() ? null : bean.destroyMethod(),
                        // @PostConstruct / @PreDestroy method:
                        null, null);
                addBeanDefinitions(defs,def);
            }
        }
    }

    Set<String> scanForClassNames(Class<?> configClass) throws IOException , URISyntaxException {
        ComponentScan scan = ClassUtils.findAnnotation(configClass, ComponentScan.class);
        String[] scanPackages = scan == null || scan.value().length == 0 ?
                new String[]{configClass.getPackage().getName()} : scan.value();
        Set<String> classNameSet = new HashSet<>();
        for (String pkg : scanPackages){
            ResourceResolver rr = new ResourceResolver(pkg);
            List<String> classList = rr.scan(
                    res -> {
                        String name = res.name;
                        if (name.endsWith(".class")){
                            return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
                        }
                        return null;
                    }
            );
            classNameSet.addAll(classList);
        }
        Import importConfig = configClass.getAnnotation(Import.class);
        if (importConfig != null) {
            for (Class<?> importConfigClass : importConfig.value()) {
                String importClassName = importConfigClass.getName();
                classNameSet.add(importClassName);
            }
        }
        return classNameSet;
    }

    Constructor<?> getSuitableConstructor(Class<?> clazz) {
        Constructor<?>[] cons = clazz.getConstructors();
        if (cons.length == 0) {
            cons = clazz.getDeclaredConstructors();
            if (cons.length != 1) {
                throw new BeanDefinitionException("More than one constructor found in class " + clazz.getName() + ".");
            }
        }
        if (cons.length != 1) {
            throw new BeanDefinitionException("More than one public constructor found in class " + clazz.getName() + ".");
        }
        return cons[0];
    }

    int getOrder(Class<?> clazz) {
        Order order = clazz.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }
    int getOrder(Method method) {
        Order order = method.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    public  void addBeanDefinitions(Map<String,BeanDefinition> defs,BeanDefinition def){
        if (defs.put(def.getName(),def)!=null){
            throw new BeanDefinitionException("Duplicate bean name: " + def.getName());
        }
    }

    public BeanDefinition findBeanDefinition(String name) {
        return this.beans.get(name);
    }

    public BeanDefinition findBeanDefinition(String name, Class<?> requiredType) {
        BeanDefinition def = findBeanDefinition(name);
        if (def == null) {
            return null;
        }
        if (!requiredType.isAssignableFrom(def.getBeanClass())) {
            throw new BeanNotOfRequiredTypeException(String.format("Autowire required type '%s' but bean '%s' has actual type '%s'.", requiredType.getName(),
                    name, def.getBeanClass().getName()));
        }
        return def;
    }

    public List<BeanDefinition> findBeanDefinitions(Class<?> type) {
        return this.beans.values().stream()
                // filter by type and sub-type:
                .filter(def -> type.isAssignableFrom(def.getBeanClass()))
                // 排序:
                .sorted().collect(Collectors.toList());
    }

    public BeanDefinition findBeanDefinition(Class<?> type){
        List<BeanDefinition> defs = findBeanDefinitions(type);
        if (defs.isEmpty()){
            return null;
        }
        if (defs.size() == 1) {
            return defs.get(0);
        }
        List<BeanDefinition> primaryDefs = defs.stream().filter(def -> def.isPrimary()).collect(Collectors.toList());
        if (primaryDefs.size() == 1) {
            return primaryDefs.get(0);
        }
        return null;
    }

    public boolean isConfigurationDefinition(BeanDefinition bean){
        return ClassUtils.findAnnotation(bean.getBeanClass(),Configuration.class) != null;

    }

    public boolean isBeanPostProcessorDefinition(BeanDefinition def){
        return BeanPostProcessor.class.isAssignableFrom(def.getBeanClass());
    }

    public  <T> T getBean(String factoryName){
        BeanDefinition def = findBean(factoryName);
        if (def == null || def.getInstance() == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s'.", factoryName));
        }
        return (T) def.getInstance();
    }
    public <T> T getBean(String name, Class<T> requiredType) {
        T t = findBean(name, requiredType);
        if (t == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s' and type '%s'.", name, requiredType));
        }
        return t;
    }

    public <T> T getBean(Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(requiredType);
        if (def == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with type '%s'.", requiredType));
        }
        return (T) def.getRequiredInstance();
    }

    public <T> T findBean(String name, Class<T> requiredTyp){
        BeanDefinition def = findBeanDefinition(name, requiredTyp);
        if (def == null) {
            return null;
        }
        return (T) def.getRequiredInstance();
    }

    public <T> T findBean (String name){
        BeanDefinition def = findBeanDefinition(name);
        if (def == null) {
            return null;
        }
        return (T) def.getRequiredInstance();
    }

    public <T> T findBean (Class<T> requiredType){
        BeanDefinition def = findBeanDefinition(requiredType);
        if (def == null) return null;
        return (T) def.getRequiredInstance();
    }

    protected <T> List<T> findBeans(Class<T> requiredType) {
        return findBeanDefinitions(requiredType).stream().map(def -> (T) def.getRequiredInstance()).collect(Collectors.toList());
    }

    public void callMethod(Object beanInstance , Method method, String namedMethod){
        if (method != null) {
            try {
                method.invoke(beanInstance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        } else if (namedMethod != null) {
            Method named = ClassUtils.getNamedMethod(beanInstance.getClass(), namedMethod);
            named.setAccessible(true);
            try {
                named.invoke(beanInstance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        }
    }

    Object getProxiedInstance(BeanDefinition def) {
        Object beanInstance = def.getInstance();
        // 如果Proxy改变了原始Bean，又希望注入到原始Bean，则由BeanPostProcessor指定原始Bean:
        List<BeanPostProcessor> reversedBeanPostProcessors = new ArrayList<>(this.beanPostProcessors);
        Collections.reverse(reversedBeanPostProcessors);
        for (BeanPostProcessor beanPostProcessor : reversedBeanPostProcessors) {
            Object restoredInstance = beanPostProcessor.postProcessOnSetProperty(beanInstance, def.getName());
            if (restoredInstance != beanInstance) {
                beanInstance = restoredInstance;
            }
        }
        return beanInstance;
    }

    @Override
    public boolean containsBean(String name) {
        return this.beans.containsKey(name);
    }

    @Override
    public <T> List<T>  getBeans(Class<T> requiredType) {
        List<BeanDefinition> defs = findBeanDefinitions(requiredType);
        if (defs == null) return new ArrayList<>();
        List<T> list = new ArrayList<>(defs.size());
        for (BeanDefinition def : defs) {
            list.add((T) def.getRequiredInstance());
        }
        return list;
    }

    @Override
    public void close() {
        this.beans.values().forEach(def -> {
            final Object beanInstance = getProxiedInstance(def);
            callMethod(beanInstance, def.getDestroyMethod(), def.getDestroyMethodName());
        });
        this.beans.clear();
        ApplicationContextUtils.setApplicationContext(null);
    }



}
