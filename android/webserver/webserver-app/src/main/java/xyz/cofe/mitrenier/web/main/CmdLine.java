package xyz.cofe.mitrenier.web.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class CmdLine {
    protected List<String> args;

    public CmdLine(){
        this.args = new ArrayList<>();
    }

    public CmdLine(CmdLine sample){
        if( sample==null ) throw new IllegalArgumentException("sample==null");
        this.args = sample.args;
        if( sample.commands!=null ) {
            this.commands = new ArrayList<>(sample.commands);
        }
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public CmdLine clone(){
        return new CmdLine(this);
    }

    public boolean isEmpty(){ return args.isEmpty(); }
    public boolean isNonEmpty(){ return !args.isEmpty(); }

    public <A1> Optional<CmdLineParsedA1<A1>> parseArg(Arg<A1> arg){
        if( arg==null ) throw new IllegalArgumentException("arg==null");
        if( isEmpty() )return Optional.empty();

        var parseResult = arg.parse(args, 0);
        if( parseResult.isEmpty() ) return Optional.empty();

        var args = new ArrayList<>(this.args);
        var len = parseResult.get()._2();
        if( len>0 ){
            for( var i=0;i<len;i++ ){
                args.remove(0);
            }
        }

        var res = new CmdLineParsedA1<>(this, parseResult.get()._1());
        res.args = args;
        return Optional.of(res);
    }

    public static class CmdLineParsedA1<A1> extends CmdLine {
        private final A1 arg1;
        public A1 value(){ return arg1; }

        public CmdLineParsedA1(CmdLine sample, A1 arg1) {
            super(sample);
            this.arg1 = arg1;
        }
    }

    public record Command<T>(
        Arg<T> syntax,
        Consumer<T> executor,
        Optional<String> description
    ) {
    }

    protected List<Command<?>> commands;

    public <T> CmdLine add(Arg<T> syntax, Consumer<T> executor){
        if( syntax==null ) throw new IllegalArgumentException("syntax==null");
        if( executor==null ) throw new IllegalArgumentException("executor==null");

        if( commands==null ) commands = new ArrayList<>();
        commands.add(new Command<T>( syntax, executor, Optional.empty() ));

        return this;
    }

    public void parse(String... cmdLineArgs){
        if( cmdLineArgs==null ) throw new IllegalArgumentException("cmdLineArgs==null");
        parse( new ArrayList<String>(Arrays.asList(cmdLineArgs)) );
    }

    public void parse(List<String> args){
        if( args==null ) throw new IllegalArgumentException("args==null");

        var cmdl = clone();
        cmdl.args = args;

        if( commands==null || commands.isEmpty() )return;

        while( cmdl.isNonEmpty() ){
            var matched = false;
            for( var cmd : commands ){
                var value = cmdl.parseArg(cmd.syntax());
                if( value.isEmpty() ){
                    continue;
                }

                var exec = (Consumer)cmd.executor();
                exec.accept( value.get().value() );

                matched = true;
                cmdl = value.get();
                break;
            }

            if( !matched ){
                break;
            }
        }
    }
}
