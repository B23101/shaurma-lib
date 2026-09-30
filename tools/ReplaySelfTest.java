// Запуск: javac -d /tmp/rt lib-common/src/main/java/dev/shaurmalib/common/camera/replay/*.java tools/ReplaySelfTest.java && java -cp /tmp/rt ReplaySelfTest
import dev.shaurmalib.common.camera.replay.*;
public class ReplaySelfTest {
  static void check(boolean c, String m){ if(!c) throw new AssertionError(m); System.out.println("ok: "+m); }
  public static void main(String[] a){
    // спільний інтервал
    ReplayClip c1 = ReplayClip.builder("a").sharedIntervalMs(1000).interpolation(ReplayInterpolation.LINEAR)
      .point(0,0,0,0,0).point(10,0,0,90,0).point(10,10,0,180,0).build();
    check(c1.naturalDurationMs()==2000, "shared: 2 segments x 1000");
    check(Math.abs(c1.sample(500).x()-5)<1e-9, "linear mid seg0 x=5");
    check(Math.abs(c1.sample(1500).y()-5)<1e-9, "linear mid seg1 y=5");
    // різні інтервали
    ReplayClip c2 = ReplayClip.builder("b").sharedIntervalMs(1000).interpolation(ReplayInterpolation.LINEAR)
      .point(0,0,0,0,0,300).point(3,0,0,0,0,0).point(3,3,0,0,0).build();
    check(c2.naturalDurationMs()==1300, "mixed: 300 + shared 1000");
    check(Math.abs(c2.sample(150).x()-1.5)<1e-9, "mixed first seg 300ms");
    check(Math.abs(c2.sample(300).x()-3)<1e-9, "boundary at 300");
    // обрив коротше природного
    ReplayClip c3 = ReplayClip.builder("c").sharedIntervalMs(1000).cutAtMs(500).point(0,0,0,0,0).point(10,0,0,0,0).build();
    check(c3.durationMs()==500 && c3.naturalDurationMs()==1000, "cut earlier than natural");
    // обрив довший — тримає останній кадр
    ReplayClip c4 = ReplayClip.builder("d").sharedIntervalMs(1000).cutAtMs(5000).point(0,0,0,0,0).point(10,0,0,0,0).build();
    check(c4.durationMs()==5000 && Math.abs(c4.sample(4000).x()-10)<1e-9, "cut later than natural holds last frame");
    // одна точка
    ReplayClip c5 = ReplayClip.builder("e").cutAtMs(1234).point(1,2,3,45,10).build();
    check(c5.durationMs()==1234 && c5.sample(999).x()==1, "single point hold");
    // yaw коротким шляхом 350 -> 10
    ReplayClip c6 = ReplayClip.builder("f").sharedIntervalMs(1000).interpolation(ReplayInterpolation.LINEAR)
      .point(0,0,0,350,0).point(0,0,0,10,0).build();
    float y = c6.sample(500).yaw();
    check(Math.abs(y)<1e-3, "yaw shortest path mid = 0 (got "+y+")");
    // spline проходить через точки
    ReplayClip c7 = ReplayClip.builder("g").sharedIntervalMs(1000)
      .point(0,0,0,0,0).point(5,5,0,0,0).point(10,0,5,0,0).point(20,0,0,0,0).build();
    check(Math.abs(c7.sample(1000).x()-5)<1e-9 && Math.abs(c7.sample(2000).z()-5)<1e-9, "spline hits keypoints");
    // сценарій: три клипи + стеля
    ReplayScript s = ReplayScript.of(4000, c1, c3, c4);   // 2000 + 500 + 5000 = 7500 planned
    check(s.plannedMs()==7500 && s.totalMs()==4000, "script ceiling 4000 < planned 7500");
    check(s.locate(1999).clipIndex()==0 && s.locate(2000).clipIndex()==1 && s.locate(2500).clipIndex()==2, "locate clips");
    check(s.locate(3999).finished()==false && s.locate(4000).finished(), "forced interrupt at 4000");
    check(s.sampleAt(4000)==null, "no pose after cut");
    ReplayScript s2 = ReplayScript.of(0, c1, c3);
    check(s2.totalMs()==2500, "no ceiling → sum");
    ReplayScript s3 = ReplayScript.of(9000, c1);        // стеля довша за клип → тримає
    check(s3.sampleAt(8000)!=null && Math.abs(s3.sampleAt(8000).y()-10)<1e-9, "ceiling longer than clip holds last frame");
    check(s.waypoints(250).size()>=16, "waypoints sampled: "+s.waypoints(250).size());
    check(ReplayScript.empty().isEmpty() && ReplayScript.empty().waypoints(250).isEmpty(), "empty script");
    System.out.println("ALL OK");
  }
}
