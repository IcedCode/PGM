package tc.oc.pgm.payload;

public class PayloadCheckpoint {

  private final int index;
  private final boolean permanent;

  private Boolean reached;

  PayloadCheckpoint(int index, boolean permanent) {
    this.index = index;
    this.permanent = permanent;
  }

  public int getIndex() {
    return index;
  }

  public boolean isReached() {
    return reached;
  }

  public boolean isPermanent() {
    return permanent;
  }
}
