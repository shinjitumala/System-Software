import java.io.IOException;

public class xv6_img_tester {
  private static int error_count = 0;

  public static void main(String[] args) {
    if (args.length != 1) {
      try {
        for (int i = 1; i < 13; i++) {
          String path = "sample\\fs" + extend(i) + ".img";
          xv6_img_file test_file = new xv6_img_file(path);
          System.out.println("sample\\fs" + extend(i) + ".img:");
          System.out.println(test_file);
          System.out.println("Found " + error_count + " errors.");
          System.out.println();
          error_count = 0;
        }
      } catch (IOException | InterruptedException e) {
        System.err.println("Error reading file!");
        e.printStackTrace();
        System.exit(1);
      }
    } else {

      xv6_img_file test_file = null;
      try {
        test_file = new xv6_img_file(args[0]);
      } catch (IOException | InterruptedException e) {
        System.out.println("Error reading file!");
        System.exit(1);
      }
      System.out.println(test_file);

      System.out.println("Found " + error_count + " errors.");
    }
  }

  public static void error() {
    error_count++;
  }

  public static String extend(int i) {
    if (i < 10) {
      return "0" + i;
    } else {
      return "" + i;
    }
  }
}
