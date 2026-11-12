import dev.shaurmalib.common.playeranim.PoseLifecycleTest;
import dev.shaurmalib.common.playeranim.PoseSourceTest;
import dev.shaurmalib.forge.playeranim.PoseEaseParityTest;
import dev.shaurmalib.forge.playeranim.PlayerPoseControllerTest;
import dev.shaurmalib.forge.playeranim.PoseLayerRuntimeTest;
import dev.shaurmalib.harness.T;

public final class Main {
    public static void main(String[] args) {
        PoseLifecycleTest.run();
        PoseSourceTest.run();
        PoseEaseParityTest.run();
        PoseLayerRuntimeTest.run();
        PlayerPoseControllerTest.run();
        System.out.println("\n==========================================");
        System.out.println("PASS: " + T.pass + "   FAIL: " + T.fail);
        System.exit(T.fail == 0 ? 0 : 1);
    }
}
