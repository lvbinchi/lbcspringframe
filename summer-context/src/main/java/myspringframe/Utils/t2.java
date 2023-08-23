package myspringframe.Utils;

import myspringframe.annotation.Autowired;
import myspringframe.annotation.Component;

@Component
public class t2
{
    @Autowired
    t1 t;
    public t2(){

    }
    public void show(){
        System.out.println(t.getA());
    }
}
