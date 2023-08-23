package myspringframe.context;

import myspringframe.annotation.ComponentScan;
import myspringframe.annotation.Import;
import myspringframe.imported.LocalDateConfiguration;

@ComponentScan
@Import(LocalDateConfiguration.class)
public class ScanApplication {
}
