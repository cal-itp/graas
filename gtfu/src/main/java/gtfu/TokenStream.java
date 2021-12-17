package gtfu;

public class TokenStream {
    String[] list;
    int index;

    public TokenStream(String s) {
        this(s, " ");
    }

    public TokenStream(String s, String delimiters) {
        list = s.split(delimiters);
        index = 0;
    }

    public String get() {
        if (index >= list.length) return null;
        else return list[index++];
    }

    public void unget() {
        if (index > 0) index--;
    }
}