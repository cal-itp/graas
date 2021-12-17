package gtfu;

public class ConsoleProgressObserver implements ProgressObserver {
    private int width;
    private int max;

    public ConsoleProgressObserver(int width) {
        this.width = width;
        System.out.println();
    }

    public void setMax(int max) {
        this.max = max;
    }

    public void update(int current) {
        StringBuilder sb = new StringBuilder();
        float fraction = current / (float)max;
        int n = (int)Math.round(width * fraction);

        sb.append('[');

        for (int i=0; i<width; i++) {
            sb.append(i <= n ? '=' : ' ');
        }

        sb.append(']');
        sb.append(current >= max ? '\n' : '\r');

        System.out.print(sb.toString());
        System.out.flush();
    }

    public void tick() {
        // ### implement me
    }
}