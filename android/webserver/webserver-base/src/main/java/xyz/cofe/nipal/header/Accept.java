package xyz.cofe.nipal.header;

import xyz.cofe.coll.im.ImList;

import java.util.Optional;

public record Accept(ImList<MediaType> media) {
    public Accept {
        if( media==null ) throw new IllegalArgumentException("media==null");
    }

    public static Optional<Accept> parse(String str){
        if( str==null )return Optional.empty();
        return AcceptParser.parse(str);
    }

    public ImList<String> preferredTypes(){
        return media.sort(MediaType::weight).map(MediaType::mime);
    }
}
