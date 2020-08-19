package tc.oc.pgm.payload;

public class PayloadCheckpoint {

  private final int index;
  private final boolean permanent;

  PayloadCheckpoint(int index, boolean permanent) {
    this.index = index;
    this.permanent = permanent;
  }

  public int index() {
    return index;
  }

  public boolean isPermanent() {
    return permanent;
  }
}
