# SignPath 接入准备

SignPath Foundation 审核通过后，在 SignPath 和 GitHub 中完成以下一次性配置。

## SignPath

1. 为 `MC DLSS Prototype` 项目连接 GitHub 仓库。
2. 安装并授权 SignPath GitHub App 访问本仓库。
3. 新建 Artifact Configuration，并使用
   [`.signpath/artifact-configuration.xml`](../.signpath/artifact-configuration.xml)。
4. 新建需要人工批准的 release signing policy。
5. 创建仅有提交签名请求权限的 API Token。

Artifact Configuration 只给 `mc_dlss_bootstrap.dll` 和
`mc_dlss_native.dll` 添加项目签名。三个 AMD FidelityFX DLL 只验证既有
Authenticode 签名，并且签名前后必须保持字节完全相同。

## GitHub

在仓库 Actions variables 中配置：

- `SIGNPATH_ENABLED=true`
- `SIGNPATH_ORGANIZATION_ID`
- `SIGNPATH_PROJECT_SLUG`
- `SIGNPATH_SIGNING_POLICY_SLUG`
- `SIGNPATH_ARTIFACT_CONFIGURATION_SLUG`

在 Actions secrets 中配置：

- `SIGNPATH_API_TOKEN`

创建 `release-signing` Environment，并限制为维护者人工批准。不要把 API
Token、证书或私钥提交到仓库。

## 发布

1. 从通过 CI 的提交创建版本标签，例如 `v0.1.0-alpha.2`。
2. 在 GitHub Actions 中选择 `Release signed FSR3 Mod`。
3. Run workflow 时必须从该版本标签运行，不能从 `main` 运行。
4. 在 GitHub Environment 和 SignPath 中分别完成人工批准。

工作流将重新构建原生 DLL，提交 SignPath，验证签名及第三方 DLL 哈希，
然后生成签名原生包和可直接放入 NeoForge `mods` 目录的实验 Mod JAR。
