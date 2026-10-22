# Terraform 로컬 실습 가이드 — Step 1: 로컬 파일 만들기

AWS 같은 클라우드 계정 없이, 내 컴퓨터에 `hello.txt` 파일 하나를 Terraform으로 만들어 보는 첫 실습이다.
`init → validate → plan → apply` 흐름을 익히는 것이 목표다.

## 0. 준비: Terraform 설치 확인

```bash
terraform -version
```

- 버전이 나오면 설치된 것이다. (이 실습은 Terraform v1.16.4에서 확인했다.)
- `command not found`가 나오면 Homebrew로 설치한다.

```bash
brew install hashicorp/tap/terraform
```

## 1. 실습 폴더 만들기

저장소 루트(`ipo-calc`)에서 실행한다.

```bash
mkdir -p terraform-study/step1
cd terraform-study/step1
```

완성 후 폴더 구성은 다음과 같다.

```text
terraform-study/
└── step1/
    ├── main.tf                 # 직접 작성하는 Terraform 설정
    ├── .terraform/             # terraform init이 받은 provider 플러그인 (자동 생성)
    ├── .terraform.lock.hcl     # provider 버전 고정 파일 (자동 생성, 커밋 대상)
    ├── terraform.tfstate       # apply 결과 기록 (자동 생성, 커밋하지 않는다)
    └── hello.txt               # apply로 만들어진 결과 파일
```

숨김 파일까지 보려면 `ls -A`를 쓴다. `terraform init` 직후에는 `.terraform`, `.terraform.lock.hcl`, `main.tf` 세 개가 보인다.

## 2. main.tf 작성

`terraform-study/step1/main.tf`:

```hcl
terraform {
  required_providers {
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

resource "local_file" "hello" {
  filename = "${path.module}/hello.txt"
  content  = "안녕 테라폼\n"
}
```

| 부분 | 뜻 |
|---|---|
| `required_providers` | 이 프로젝트에 필요한 플러그인(provider) 목록 |
| `local` / `hashicorp/local` | 내 컴퓨터의 파일을 관리하는 provider |
| `version = "~> 2.5"` | 2.5 이상 3.0 미만의 최신 버전 (실습에서는 v2.9.1이 설치됨) |
| `resource "local_file" "hello"` | `local_file` 종류의 리소스를 `hello`라는 내부 이름으로 만든다 |
| `filename` | 만들 파일 경로. `${path.module}`은 이 `main.tf`가 있는 폴더 |
| `content` | 파일에 쓸 내용. `\n`은 줄바꿈 |

즉 "이 폴더에 `hello.txt`를 만들고 안에 `안녕 테라폼`을 적어라"는 선언이다. 클라우드를 쓰지 않으니 AWS 인증도 필요 없다.

## 3. 실행하기

`terraform-study/step1`에서 순서대로 실행한다.

### ① 초기화 — `terraform init`

```bash
terraform init
```

`hashicorp/local` provider를 내려받아 `.terraform/`에 넣고, 버전을 `.terraform.lock.hcl`에 기록한다.

```text
- Finding hashicorp/local versions matching "~> 2.5"...
- Installing hashicorp/local v2.9.1...
- Installed hashicorp/local v2.9.1 (signed by HashiCorp)
...
Terraform has been successfully initialized!
```

### ② 설정 검사 — `terraform validate`

```bash
terraform validate
```

```text
Success! The configuration is valid.
```

문법과 구조만 검사한다. 실제로 무엇이 만들어질지는 보여 주지 않는다.

### ③ 변경 예정 확인 — `terraform plan`

```bash
terraform plan
```

실제로는 아무것도 바꾸지 않고, 적용하면 어떻게 되는지만 보여 준다.

```text
  # local_file.hello will be created
  + resource "local_file" "hello" {
      + content              = <<-EOT
            안녕 테라폼
        EOT
      + directory_permission = "0777"
      + file_permission      = "0777"
      + filename             = "./hello.txt"
      + id                   = (known after apply)
      ...
    }

Plan: 1 to add, 0 to change, 0 to destroy.
```

- `+ create`는 새로 만든다는 뜻이다.
- `(known after apply)`는 적용한 뒤에야 값이 정해지는 항목이다(해시, id 등).
- 마지막 줄 `1 to add, 0 to change, 0 to destroy`가 핵심이다. 만들 것 1개, 바꿀 것·지울 것 0개.
- `-out` 옵션 없이 실행했다는 안내(Note)는 실습에서는 무시해도 된다.

### ④ 실제 적용 — `terraform apply`

```bash
terraform apply
```

plan과 같은 내용을 한 번 더 보여 주고 확인을 묻는다. `yes`를 입력한다.

```text
Do you want to perform these actions?
  Enter a value: yes

local_file.hello: Creating...
local_file.hello: Creation complete after 0s [id=a261ee3c5176bf9128d00ae8648d1d20359dbca5]

Apply complete! Resources: 1 added, 0 changed, 0 destroyed.
```

### ⑤ 결과 확인

```bash
cat hello.txt
```

```text
안녕 테라폼
```

## 4. 자동으로 생긴 파일 이해하기

`init`과 `apply`를 하면 내가 쓰지 않은 파일이 생긴다. 각각 무엇인지 비유로 정리한다.

| | `.terraform/` | `.terraform.lock.hcl` | `terraform.tfstate` |
|---|---|---|---|
| 비유 | 부품 창고 | 버전 고정 메모 | 장부 |
| 정체 | `init`이 받아 온 provider 플러그인 프로그램 | "이 버전으로 썼다"는 기록 | "Terraform이 무엇을 만들었는지"의 기록 |
| 만드는 명령 | `terraform init` | `terraform init` | `terraform apply` |
| Git에 올리나 | 아니오 (`.gitignore`) | **예** | 아니오 (`.gitignore`) |
| 지우면 | `init` 다시 하면 복구 | 팀원 간 버전이 어긋날 수 있음 | Terraform이 만든 것을 잊어버림 (지우지 말 것) |

### 4-1. `.terraform/` — 부품 창고

Terraform 자체는 "무엇을 만들 수 있는지" 모른다. 파일 만들기, AWS 자원 만들기 같은 능력은 **provider**라는 별도 플러그인이 가지고 있다. `init`은 필요한 provider를 인터넷에서 받아 오고, 받은 것이 `.terraform/`에 저장된다.

`main.tf`의 두 줄이 "무엇을 받을지"를 정한다.

| 코드 | 의미 |
|---|---|
| `source = "hashicorp/local"` | "hashicorp라는 곳의 local 부품을 가져와" |
| `version = "~> 2.5"` | "2.5 이상 3.0 미만 버전이면 돼" |

실제로 들어 있는 파일은 이렇게 확인한다.

```bash
find .terraform -type f
```

```text
.terraform/providers/registry.terraform.io/hashicorp/local/2.9.1/darwin_arm64/LICENSE.txt
.terraform/providers/registry.terraform.io/hashicorp/local/2.9.1/darwin_arm64/terraform-provider-local_v2.9.1_x5
```

경로를 읽으면 `hashicorp/local` provider의 `2.9.1` 버전, 맥(Apple 실리콘, `darwin_arm64`)용 실행 파일이다. 이 폴더는 통째로 지워도 된다. `terraform init`을 다시 하면 같은 부품이 다시 받아진다. 그래서 Git에 올리지 않는다.

AWS provider는 수백 MB라서 폴더마다 받으면 낭비다. 한곳에 모아 두는 설정이 있다.

```bash
export TF_PLUGIN_CACHE_DIR=$HOME/.terraform.d/plugin-cache
```

### 4-2. `.terraform.lock.hcl` — 버전 고정 메모

`version = "~> 2.5"`는 **범위**일 뿐이다. 범위 안에서 실제로 어떤 버전을 받았는지는 이 파일이 기록한다.

```hcl
provider "registry.terraform.io/hashicorp/local" {
  version     = "2.9.1"     # 실제로 받은 정확한 버전
  constraints = "~> 2.5"    # 코드에 적은 허용 범위
  hashes = [ "h1:...", ... ] # 부품이 변조되지 않았는지 확인하는 지문
}
```

| 상황 | 락 파일이 없으면 | 락 파일이 있으면 |
|---|---|---|
| 철수가 오늘 `init` | 그때 최신인 2.9.1을 받음 | 2.9.1 |
| 영희가 한 달 뒤 `init` | 새로 나온 더 높은 버전을 받음 | 같은 2.9.1을 받음 |

버전이 다르면 같은 코드가 다르게 동작할 수 있어서, 모두가 같은 버전을 쓰게 고정한다. 지우지 말고 Git에 올린다.

### 4-3. `terraform.tfstate` — 장부 (state)

장부는 Terraform이 **"내가 무엇을 만들었는지" 적어 두는 파일**이다.

#### 왜 장부가 필요할까

Terraform은 매번 AWS 같은 곳을 처음부터 뒤져서 "이게 내가 만든 건가?"를 알아낼 수 없다. 그래서 자기가 만든 것을 파일에 적어 둔다. `plan`이 하는 비교도 사실 **코드와 장부의 비교**다.

```text
코드(main.tf)  : "hello 파일이 있어야 해"
장부(tfstate)  : "hello 파일을 내가 만들었어"
→ 둘이 같으면 "변경 없음"
```

#### ① 장부에 뭐가 있는지 목록 보기 — `terraform state list`

```bash
terraform state list
```

```text
local_file.hello
```

코드의 `resource "local_file" "hello"`와 같은 이름(`종류.이름`)이다. AWS 프로젝트라면 `aws_db_instance.main`, `aws_instance.app` 같은 줄이 수십 개 나온다.

#### ② 하나만 자세히 보기 — `terraform state show`

```bash
terraform state show local_file.hello
```

```text
# local_file.hello:
resource "local_file" "hello" {
    content              = <<-EOT
        안녕 테라폼
    EOT
    content_md5          = "aff6965c450efb7c101a585628a74ecf"
    content_sha1         = "a261ee3c5176bf9128d00ae8648d1d20359dbca5"
    directory_permission = "0777"
    file_permission      = "0777"
    filename             = "./hello.txt"
    id                   = "a261ee3c5176bf9128d00ae8648d1d20359dbca5"
    ...
}
```

`id`와 해시값(`content_md5`, `content_sha1` 등)은 `apply`가 끝나야 알 수 있는 값이다. `plan`에서 `(known after apply)`였던 항목들을 장부가 기억하고 있다.

#### ③ 장부 파일을 직접 열어 보기 — `cat terraform.tfstate`

```bash
cat terraform.tfstate | head -30
```

```text
{
  "version": 4,
  "terraform_version": "1.16.4",
  "serial": 1,
  "resources": [
    {
      "mode": "managed",
      "type": "local_file",
      "name": "hello",
      "instances": [
        { "attributes": {
            "content": "안녕 테라폼\n",
            "filename": "./hello.txt",
            "id": "a261ee3c5176bf9128d00ae8648d1d20359dbca5",
            ...
```

처음에는 아래 네 가지만 찾아보면 된다.

| 찾을 것 | 의미 |
|---|---|
| `"version"` | 장부 파일 형식의 버전 |
| `"terraform_version"` | 이 장부를 마지막으로 쓴 Terraform 버전 |
| `"resources"` 안의 `"type": "local_file"` | 방금 만든 리소스 (`name`이 `hello`) |
| `"attributes"` | 그 리소스의 실제 값 (`id`가 `apply` 때 본 `[id=a261ee…]`와 같다) |

`serial`은 장부가 바뀔 때마다 올라가는 번호다.

> **장부는 절대 손으로 고치지 않는다.** 장부가 현실과 어긋나면 Terraform이 엉뚱한 판단을 한다.

#### 왜 중요할까 (연습과 실제 클라우드 작업 비교)

| | 이 연습 | 실제 클라우드(AWS 등) |
|---|---|---|
| 장부 위치 | 내 폴더의 `terraform.tfstate` | 보통 S3 같은 원격 저장소에 둔다 |
| 잃어버리면 | 연습 파일이라 상관없다 | Terraform이 자기가 만든 DB·서버를 잊어버려 **관리 불능**이 된다 |
| 비밀값이 들어가나 | 없다 | **들어갈 수 있다** (비밀번호 등이 평문으로 기록될 수 있다) |

그래서 실제 작업에서는 장부를 `.gitignore`에 넣고 Git에 올리지 않으며, 원격 저장소는 암호화한다. 또 비밀번호 같은 값은 Terraform 코드에 넣지 않고 따로 설정한다. 값이 장부에 그대로 남기 때문이다.

## 5. `plan`과 `apply`의 차이

쉽게 말하면 **`plan`은 미리보기, `apply`는 실제 실행**이다. Terraform은 내가 쓴 `.tf` 코드와 지금 관리 중인 상태를 비교해서 무엇을 바꿔야 하는지 판단하는데, 두 명령은 그 결과를 다루는 방식이 다르다.

| | `terraform plan` | `terraform apply` |
|---|---|---|
| 하는 일 | 코드와 현재 상태를 비교해 **변경 계획**을 보여 준다 | 변경 계획을 **실제로 적용**한다 (생성·수정·삭제) |
| 비유 | 미리보기 / 견적서 | 실행 / 공사 시작 |
| 파일이 만들어지나 | 만들어지지 않는다 | 만들어진다 |
| 몇 번 실행해도 | 안전하다 | 현실이 바뀐다 |

`plan`에 나오는 "`hello.txt`가 **만들어질 예정**"은 아직 만들어졌다는 뜻이 아니다. `apply`를 하고 `yes`를 입력해야 실제로 `hello.txt`가 생긴다.

### 예제: 코드를 고치면 어떻게 될까

처음 `apply`로 `hello.txt`(내용 `안녕 테라폼`)를 만든 뒤, `main.tf`의 `content`를 바꿔 본다.

```hcl
content  = "안녕 테라폼\n"      # 바꾸기 전
content  = "테라폼 재밌다\n"    # 바꾼 후
```

**① `terraform plan` — 아직 `hello.txt`는 그대로다.**

```text
  # local_file.hello must be replaced
-/+ resource "local_file" "hello" {
      ~ content = <<-EOT # forces replacement
          - 안녕 테라폼
          + 테라폼 재밌다
        EOT
        ...
    }

Plan: 1 to add, 0 to change, 1 to destroy.
```

- `-`(지울 줄)은 기존 내용, `+`(새 줄)은 바뀔 내용이다.
- `-/+`와 `forces replacement`는 "이 항목은 제자리에서 못 고치니 **지우고 다시 만든다**"는 뜻이다. 그래서 요약이 `1 to add, 1 to destroy`다. 이때 `cat hello.txt`를 하면 아직 `안녕 테라폼`이다.

**② `terraform apply` — 이제 실제로 바뀐다.**

```text
local_file.hello: Creating...
Apply complete! Resources: 1 added, 0 changed, 1 destroyed.
```

```bash
cat hello.txt
```

```text
테라폼 재밌다
```

이처럼 항상 **고친다 → `plan`으로 확인 → `apply`로 적용** 순서로 일한다. 같은 `content` 변경이라도 리소스 종류에 따라 `~`(제자리 수정)가 나올 수도 있다. 어느 쪽인지는 `plan`을 읽어야 알 수 있다.

### 5-1. `plan` 결과 읽는 법

`plan`은 **아무것도 만들지 않고** 계획만 보여 준다. 건축 도면을 보고 "이 건물을 지으려면 이런 공사가 필요하다"는 견적서를 뽑는 단계라서 몇 번을 실행해도 안전하다. 코드를 고칠 때마다 `plan`을 먼저 해서 영향을 확인한다.

내부에서는 세 가지를 비교한다.

```text
코드(main.tf)   ──┐
                  ├─ 비교 ──▶ 차이 = 해야 할 일
장부(tfstate)   ──┤
현실(실제 상태) ──┘
```

지금은 장부가 비어 있고 현실에도 `hello.txt`가 없으니 "새로 만들어야 한다"가 나온다.

#### 출력 읽는 순서

```text
  + create                                  ← ① 이번 계획에 쓰인 기호 범례

  # local_file.hello will be created        ← ② 어떤 리소스를 어떻게 하는지
  + resource "local_file" "hello" {         ← ③ 앞의 + = 새로 만든다
      + content = <<-EOT
            안녕 테라폼
        EOT
      + id      = (known after apply)       ← ④ 만들어 봐야 아는 값

Plan: 1 to add, 0 to change, 0 to destroy.  ← ⑤ 요약
```

| 번호 | 읽는 법 |
|---|---|
| ① | 맨 위 범례 |
| ② | `local_file.hello`는 `종류.이름`. 코드의 `resource "local_file" "hello"`와 같다 |
| ③ | `+`는 새로 만든다 |
| ④ | `(known after apply)`는 만든 뒤에야 정해지는 값(id, 해시 등). 지금 모르는 게 정상 |
| ⑤ | **가장 중요한 줄.** 추가·변경·삭제가 각각 몇 개인지 |

#### 기호 4가지 (AWS에서도 그대로 쓰인다)

| 기호 | 뜻 | 위험도 |
|---|---|---|
| `+` | 새로 만든다 | 낮음 |
| `~` | 기존 것을 제자리에서 수정 | 보통 |
| `-` | 삭제 | 높음 |
| `-/+` | 삭제 후 다시 만듦(교체) | 높음. DB라면 데이터가 사라질 수 있음 |

실제 작업에서는 요약 줄의 `destroy`가 0인지 먼저 본다.

#### 끝에 나오는 안내문(Note)

```text
Note: You didn't use the -out option to save this plan, so Terraform can't
guarantee to take exactly these actions if you run "terraform apply" now.
```

"이 계획을 파일로 저장하지 않았으니, 나중에 `apply`할 때 정확히 이 계획대로 실행된다는 보장은 없다"는 뜻이다. 연습에서는 무시해도 된다. 실제 클라우드 작업에서는 읽은 계획과 실행되는 계획이 같다는 보장을 위해 이렇게 한다.

```bash
terraform plan -out p    # 계획을 p 파일로 저장
terraform apply p        # 저장한 그 계획 그대로 실행
```

## 6. `apply` 자세히 알기

`plan`이 견적서였다면 `apply`는 **공사 시작**이다. 이 단계 뒤에는 현실이 바뀐다. 지금은 파일 하나지만 AWS에서는 DB나 서버가 만들어지고 비용이 생길 수 있어서, 항상 `plan`을 먼저 읽고 `apply`한다.

### 실행 방식 두 가지

| 방식 | 명령 | 동작 |
|---|---|---|
| 그 자리에서 계획 후 실행 | `terraform apply` | 계획을 한 번 더 보여 주고 확인을 묻는다 |
| 저장한 계획 실행 | `terraform apply p` | 앞서 `plan -out p`로 저장했다면, 확인 없이 그 계획 그대로 실행한다 |

처음에는 첫 번째 방식으로 한다.

### 진행 순서

1. 계획이 다시 출력된다.
2. 확인 질문이 나온다.

   ```text
   Do you want to perform these actions?
     Terraform will perform the actions described above.
     Only 'yes' will be accepted to approve.

     Enter a value:
   ```

3. `yes`를 정확히 입력하고 엔터를 친다. `y`나 `Yes`는 받아 주지 않는다(`Only 'yes' will be accepted`). 일부러 번거롭게 만든 안전장치다.
4. 아래처럼 진행된다.

   ```text
   local_file.hello: Creating...
   local_file.hello: Creation complete after 0s [id=...]

   Apply complete! Resources: 1 added, 0 changed, 0 destroyed.
   ```

### 끝난 뒤 확인할 것

```bash
ls
cat hello.txt
```

| 확인 | 기대 |
|---|---|
| `hello.txt`가 생겼나 | 생겼다 (`plan` 때는 없었다) |
| 내용 | `안녕 테라폼` |
| 새로 생긴 다른 파일 | `terraform.tfstate` (4-3절의 장부) |

### 한 번 더 `apply` 해 보기 (중요한 실험)

```bash
terraform apply
```

이번에는 확인 질문 없이 이렇게 나온다.

```text
No changes. Your infrastructure matches the configuration.
Apply complete! Resources: 0 added, 0 changed, 0 destroyed.
```

같은 코드를 몇 번 `apply`해도 결과가 같다. 이 성질을 **멱등성**이라고 하며 Terraform의 핵심 장점이다. 이미 있으면 다시 만들지 않으므로, 실무에서 `plan`이 "변경 없음"으로 나오는 것이 정상 상태다.

### 자주 만나는 상황

| 상황 | 의미 |
|---|---|
| `yes` 대신 엔터만 침 | 취소된다(`Apply cancelled`). 아무것도 만들어지지 않는다 |
| `Error: ...` | 대부분 코드 오타. 에러 메시지의 줄 번호를 본다 |
| 확인 질문 없이 바로 진행 | `-auto-approve` 옵션을 붙였을 때. AWS에서는 습관적으로 쓰지 않는다 |

## 7. 직접 해볼 것

1. `find .terraform -type f`로 플러그인 파일이 실제로 있는지 본다.
2. `.terraform.lock.hcl`을 열어 `version =`이 몇인지 찾는다. (이 실습은 `2.9.1`)
3. `rm -rf .terraform` 후 `terraform plan`을 해 본다. provider가 없다며 `init`을 하라는 오류가 나온다. `terraform init`을 다시 하면 복구된다.
4. `terraform state list`, `terraform state show local_file.hello`, `cat terraform.tfstate`로 장부를 본다.
5. 아무것도 바꾸지 않고 `apply`를 다시 해서 `No changes`가 나오는지 확인한다.
6. `main.tf`의 `content`를 바꾸고 `plan`을 본다. 5절의 예제처럼 `-/+`(교체)가 나오는지 확인하고, `cat hello.txt`가 `plan` 직후에는 그대로, `apply` 직후에는 바뀌는지 본다.
7. 정리하려면 `terraform destroy` 후 `yes`. `hello.txt`가 삭제된다.
