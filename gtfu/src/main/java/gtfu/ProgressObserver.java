package gtfu;

public interface ProgressObserver {
    public void setMax(int max);
    public void update(int value);
    public void tick();
}