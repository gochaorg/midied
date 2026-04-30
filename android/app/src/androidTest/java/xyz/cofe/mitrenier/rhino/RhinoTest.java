package xyz.cofe.mitrenier.rhino;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class RhinoTest {
    @Test
    public void test1(){
        Context cx = Context.enter();
        cx.setInterpretedMode(true);
        try{
            var scope = cx.initStandardObjects();
            scope.put("a", scope, 1);

            ScriptableObject.putProperty(scope, "hello", new BaseFunction(){
                @Override
                public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                    System.out.println("call "+ Arrays.toString(args));
                    return Context.javaToJS("ok", scope);
                }
            });

            //System.out.println(cx.evaluateString(scope, "a", "non", 0, null));
            System.out.println(cx.evaluateString(scope,
                """
                hello( 'abc' )
                hello( 'xyz' )
                """,
                "non", 0, null));
        }finally {
            Context.exit();
        }
    }
}
