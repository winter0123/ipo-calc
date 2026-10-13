package com.vibeaiteam.brokerintegration.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.vibeaiteam.brokerintegration.adapter.BrokerQueryPort;
import com.vibeaiteam.brokerintegration.adapter.BrokerSaleSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 브로커 조회 전용 아키텍처 테스트(research 4.3의 장치 A·B·C).
 *
 * <p>AC-1820(비-GET 호출 0건), AC-1821(포트에 조회 메서드만) 을 강제한다. 위반 시 빌드가 실패한다. 이 테스트가 없으면 brokerintegration
 * 어댑터는 조회 전용이라고 주장할 수 없다.
 */
class BrokerReadOnlyArchitectureTest {

  /** 토큰 발급 외에는 어떤 HTTP 메서드 호출도 허용하지 않는다. */
  private static final Set<String> FORBIDDEN_HTTP_METHODS =
      Set.of("post", "put", "patch", "delete", "method");

  /** {@link #FORBIDDEN_HTTP_METHODS} 중 접근토큰 발급 예외로 남겨 두는 메서드. */
  private static final Set<String> AUTH_CLIENT_ALLOWED_HTTP_METHODS = Set.of("post");

  /** 포트 메서드명에 들어갈 수 없는 주문 관련 키워드. */
  private static final String ORDER_KEYWORD_PATTERN =
      "(?i).*(order|buy|sell|cancel|modify|place|submit|revise).*";

  /** 아키텍처 규칙이 적용되는 대상 패키지. */
  private static final String ADAPTER_PACKAGE = "..brokerintegration.adapter..";

  /**
   * POST가 예외로 허용되는 클래스. 접근토큰 발급(세 증권사)과, 조회가 POST인 키움·LS의 TR 조회 클라이언트뿐이다. 나머지(KIS 조회 포함)는 GET만 쓸 수
   * 있고, 여기 있는 클래스도 put/patch/delete/method는 못 쓴다.
   */
  private static final String ADAPTER_ROOT = "com.vibeaiteam.brokerintegration.adapter.";

  private static final String[] POST_ALLOWED_CLASSES = {
    ADAPTER_ROOT + "kis.KisAuthClient",
    ADAPTER_ROOT + "kiwoom.KiwoomAuthClient",
    ADAPTER_ROOT + "kiwoom.KiwoomTradeProfitClient",
    ADAPTER_ROOT + "ls.LsAuthClient",
    ADAPTER_ROOT + "ls.LsDailyTradeClient",
  };

  /** 검사 대상 메인 소스 경로(테스트는 Gradle의 backend 프로젝트 디렉터리가 작업 디렉터리다). */
  private static final Path MAIN_SRC_ROOT =
      Paths.get("src/main/java/com/vibeaiteam/brokerintegration");

  /**
   * KIS 주문 엔드포인트 경로 조각. 아래 소스 스캔 테스트는 src/main 하위만 훑기 때문에 이 테스트 파일 자신은 검사 대상이 아니지만, 스캔 범위가 넓어져도 자기
   * 자신이 걸리지 않도록 문자열을 조각으로 나눠 합친다.
   */
  private static final String[] FORBIDDEN_PATH_FRAGMENTS = {
    "trading" + "/" + "order",
    "order" + "-cash",
    "order" + "-rvsecncl",
    "order" + "-resv",
    "order" + "-credit",
    // 키움 주문 TR(kt10000~)·경로, LS 주문 경로
    "kt100" + "0",
    "dostk" + "/ordr",
    "stock" + "/order",
  };

  private static final JavaClasses MAIN_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.vibeaiteam.brokerintegration");

  @Test
  void adapter_exceptAuthClient_mustNotCallNonGetHttpMethods() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(ADAPTER_PACKAGE)
            .and()
            .doNotHaveFullyQualifiedName(POST_ALLOWED_CLASSES[0])
            .and()
            .doNotHaveFullyQualifiedName(POST_ALLOWED_CLASSES[1])
            .and()
            .doNotHaveFullyQualifiedName(POST_ALLOWED_CLASSES[2])
            .and()
            .doNotHaveFullyQualifiedName(POST_ALLOWED_CLASSES[3])
            .and()
            .doNotHaveFullyQualifiedName(POST_ALLOWED_CLASSES[4])
            .should()
            .callMethodWhere(isForbiddenHttpCall())
            .because(
                "조회 전용 어댑터는 GET만 호출할 수 있다. POST는 접근토큰 발급과 키움·LS TR 조회 클라이언트만 예외이며 "
                    + "put/patch/delete/method 호출은 어떤 클래스에서도 허용되지 않는다(AC-1820).");

    rule.check(MAIN_CLASSES);
  }

  @Test
  void postAllowedClasses_mustNotCallNonGetHttpMethodsExceptPost() {
    ArchRule rule =
        noClasses()
            .that(
                describe(
                    "POST 허용 클래스",
                    c -> java.util.Arrays.asList(POST_ALLOWED_CLASSES).contains(c.getName())))
            .should()
            .callMethodWhere(
                describe(
                    "RestClient의 put/patch/delete/method 호출",
                    call ->
                        call.getTargetOwner().isAssignableTo(RestClient.class)
                            && FORBIDDEN_HTTP_METHODS.contains(call.getName())
                            && !AUTH_CLIENT_ALLOWED_HTTP_METHODS.contains(call.getName())))
            .because("POST 허용 클래스는 POST만 호출할 수 있고 put/patch/delete/method 호출은 금지된다" + "(AC-1820).");

    rule.check(MAIN_CLASSES);
  }

  @Test
  void brokerQueryPort_methodNamesMustNotContainOrderKeywords() {
    ArchRule rule =
        methods()
            .that()
            .areDeclaredIn(BrokerQueryPort.class)
            .should()
            .haveNameNotMatching(ORDER_KEYWORD_PATTERN)
            .because(
                "BrokerQueryPort에는 조회 메서드만 선언되어야 하며 주문 관련 키워드가 메서드명에 들어가면 안 된다" + "(AC-1821).");

    rule.check(MAIN_CLASSES);
  }

  @Test
  void brokerQueryPort_mustNotDeclareMethodsBeyondDeclaredContract() {
    // 포트에 메서드가 추가될 때마다 "이것이 조회 메서드인가"를 사람이 재검토하게 만든다(research 4.3-A).
    // 주문 메서드가 추가되면 아래 어서션이 실패한다.
    assertThat(BrokerQueryPort.class.getDeclaredMethods())
        .as("BrokerQueryPort의 메서드는 조회 메서드여야 한다. 주문 메서드가 추가되면 이 테스트가 실패한다.")
        .hasSize(1);
  }

  @Test
  void brokerSaleSource_methodNamesMustNotContainOrderKeywords() {
    ArchRule rule =
        methods()
            .that()
            .areDeclaredIn(BrokerSaleSource.class)
            .should()
            .haveNameNotMatching(ORDER_KEYWORD_PATTERN)
            .because("BrokerSaleSource에는 조회 메서드만 선언되어야 한다.");

    rule.check(MAIN_CLASSES);
  }

  @Test
  void brokerSaleSource_mustNotDeclareMethodsBeyondDeclaredContract() {
    assertThat(
            java.util.Arrays.stream(BrokerSaleSource.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .map(java.lang.reflect.Method::getName))
        .as("BrokerSaleSource에 메서드가 추가되면 조회 메서드인지 사람이 재검토해야 한다.")
        .containsExactlyInAnyOrder("provider", "fetchSales", "maxRangeDays", "rangeLimitMessage");
  }

  @Test
  void adapterSources_everySaleSourceAndIssuerLivesInAdapterPackage() {
    // 새 증권사 어댑터가 POST 허용 목록 밖에서 RestClient를 쓰면 첫 규칙이 실패한다. 여기서는 어댑터 패키지가 실제로 검사 대상에 들어갔는지 확인한다.
    assertThat(
            MAIN_CLASSES.contain(
                "com.vibeaiteam.brokerintegration.adapter.kiwoom.KiwoomTradeProfitClient"))
        .isTrue();
    assertThat(
            MAIN_CLASSES.contain("com.vibeaiteam.brokerintegration.adapter.ls.LsDailyTradeClient"))
        .isTrue();
  }

  @Test
  void mainSources_mustNotContainOrderPathStrings() throws IOException {
    // given: brokerintegration 하위 모든 .java 파일
    assertThat(MAIN_SRC_ROOT).as("검사 대상 소스 경로가 존재해야 한다").exists();

    try (Stream<Path> paths = Files.walk(MAIN_SRC_ROOT)) {
      List<Path> javaFiles =
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".java"))
              .toList();
      assertThat(javaFiles).as("검사 대상 .java 파일이 1개 이상이어야 한다").isNotEmpty();

      // when & then: 각 파일에 KIS 주문 엔드포인트 문자열이 없는지 확인
      for (Path file : javaFiles) {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        for (String fragment : FORBIDDEN_PATH_FRAGMENTS) {
          assertThat(content)
              .as("파일 %s에 KIS 주문 경로 문자열 '%s'이 포함되어 있습니다.", file, fragment)
              .doesNotContain(fragment);
        }
      }
    }
  }

  private static DescribedPredicate<JavaMethodCall> isForbiddenHttpCall() {
    return describe(
        "RestClient의 post/put/patch/delete/method 호출",
        call ->
            call.getTargetOwner().isAssignableTo(RestClient.class)
                && FORBIDDEN_HTTP_METHODS.contains(call.getName()));
  }
}
