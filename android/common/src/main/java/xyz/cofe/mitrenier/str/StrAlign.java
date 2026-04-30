package xyz.cofe.mitrenier.str;

public class StrAlign {
    public static String strAlign(Object str, int len){
        if( str==null ){
            return len<1 ? "" : " ".repeat(len);
        }
        var str2 = str.toString().trim();
        if( str2.length()< len ){
            return " ".repeat( len - str2.length() )+str2;
        }
        return str2;
    }

    public static Align strAlign(Object value){
        return new Align(value);
    }

    public static class Align {
        public Object value;

        public Align(Object value) {
            this.value = value;
        }

        public int len = -1;

        public Align len(int len){
            this.len = len;
            return this;
        }

        public boolean alignLeft = false;
        public boolean alignRight = false;

        public Align left(){
            alignLeft = true;
            return this;
        }

        public Align left(int len){
            return left().len(len);
        }

        public Align left(int len, String pad){
            return left().len(len).leftPad(pad);
        }

        public Align right(){
            alignRight = true;
            return this;
        }

        public Align right(int len){
            return right().len(len);
        }

        public Align right(int len, String pad){
            return right().len(len).rightPad(pad);
        }

        private String padLeft = " ";
        public Align leftPad(String text){
            padLeft = text==null || text.isEmpty() ? " " : text;
            return this;
        }

        private String padRight = " ";
        public Align rightPad(String text){
            padRight = text==null || text.isEmpty() ? " " : text;
            return this;
        }

        public Align padding(String text){
            return leftPad(text).rightPad(text);
        }

        @Override
        public String toString() {
            var str2 = value==null ? "" : value.toString().trim();
            if( len<1 )return str2;

            if( str2.length()>=len )return str2;

            var wLen = len - str2.length();
            var wLeft = wLen;
            var wRight = 0;

            if( alignLeft==alignRight ){
                wLeft = wLen / 2;
                wRight = wLen - wLeft;
            }else{
                wLeft = alignLeft ? 0 : wLen;
                wRight = alignLeft ? wLen : 0;
            }

            var leftStr = (padLeft.repeat(wLeft)).substring(0, wLeft);
            var rightStr = (padRight.repeat(wRight)).substring(0, wRight);

            return leftStr + str2 + rightStr;
        }
    }
}
