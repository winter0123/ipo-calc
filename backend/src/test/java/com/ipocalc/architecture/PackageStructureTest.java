package com.ipocalc.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 리팩토링 중 구조가 나빠지지 않게 현재 지켜지는 규칙만 고정한다.
 *
 * <ul>
 *   <li>Epic 최상위 패키지끼리 순환 의존이 없다.
 *   <li>저장소(repository)와 엔티티(domain.model)는 웹 계층(controller)에 의존하지 않는다.
 * </ul>
 *
 * <p>서비스가 다른 패키지의 {@code controller.dto}에 의존하는 12곳은 알려진 부채라 규칙에 넣지 않았다(리팩토링 3단계에서 증권사 연동 패키지만
 * 정리한다).
 */
class PackageStructureTest {

  private static final JavaClasses MAIN_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.ipocalc");

  @Test
  void epicPackagesHaveNoCyclicDependencies() {
    slices().matching("com.ipocalc.(*)..").should().beFreeOfCycles().check(MAIN_CLASSES);
  }

  @Test
  void repositoriesAndEntitiesDoNotDependOnControllers() {
    noClasses()
        .that()
        .resideInAnyPackage("..repository..", "..domain.model..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..controller..")
        .check(MAIN_CLASSES);
  }
}
