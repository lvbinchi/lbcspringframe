package myspringframe.Utils;

import myspringframe.annotation.Component;

@Component
public class t1 {
    public int a = 1;
    public int b=2;
    public t1(){
        a =1;
        b = 999;
    }
    public int getA(){
        return b;
    }
}
