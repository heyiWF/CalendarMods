[中文](face-brightness.md) | [English](face-brightness.en.md)

# 人脸存在感应亮度

mycompat 的单页主题提供默认关闭的独立开关，仅在设备报告有摄像头时显示。Android 6 及以上需要相机运行时权限。只有该主题启用此功能、处于全屏且 Activity 在前台时才采集。实现枚举旧版相机 API 暴露的所有相机，按前置标记、ID 排序；某相机或配置失败后尝试其余候选，并限制重试频率。厂商标记错误、外接屏幕或旧 API 不暴露的镜头无法可靠推断。

检测器使用 Android 自带的开源 `android.media.FaceDetector`，API 1 起可用：[AOSP 源码](https://android.googlesource.com/platform/frameworks/base/+/nougat-release/media/java/android/media/FaceDetector.java)、[API 文档](https://developer.android.com/reference/android/media/FaceDetector)。框架源码采用 Apache-2.0；应用调用系统 API，不另行分发原生库，也不依赖 Google Play 服务。

这是有人脸／无人脸的存在检测，不是身份识别。画面仅留在内存，不保存图片、模板或身份，不新增网络或存储权限。

单个工作线程每秒分析两帧 NV21 亮度数据，复用最长边 320 像素、方向校正后的 RGB_565 位图。发现人脸时把当前窗口亮度升到 85%；连续八秒无人脸后渐暗到 2%，日历仍显示。启动同样有八秒宽限。相机错误或帧停滞时恢复原窗口亮度并自动重连；退出全屏、暂停或销毁时释放采集。不会修改全局亮度。正面、光线充分的脸更容易识别；遮挡或黑暗可能漏检，不作为安全或活体检测。分析每秒两帧不等于相机只在这两帧期间运行。

测试专用 `face-fixture.png` 为 NASA 的 Eileen Collins 公共领域照片，由 scikit-image 作为 `skimage.data.astronaut` 分发：[数据说明](https://scikit-image.org/docs/stable/api/skimage.data#skimage.data.astronaut)、[文件](https://github.com/scikit-image/scikit-image/blob/v0.24.0/skimage/data/astronaut.png)。仅在 instrumentation 包中测试正负样本与方向，不进入应用 APK，不暗示其认可本项目。

相机操作由进程级工作线程串行执行，Activity 重建后不会在旧相机释放前打开新相机。纹理销毁延迟到生产者停止后再释放。若系统不重建 Activity，显示旋转仍更新原始帧方向而不强制重启预览。实际预览尺寸／格式从驱动读回；广告尺寸组合不可用时回退默认。`FaceBrightness` 日志只包含相机 ID、方向和异常，不含画面或人脸数据。

无人值守恢复没有重试次数上限。12 秒帧看门狗重启停滞采集；失败重连从 350 ms 退避至最多 30 秒，稳定分析 30 秒后重置。暂停或退出全屏停止待执行重试。全屏常亮标志独立于检测状态，因此无人脸只降低亮度，不让屏幕休眠。物理故障或卡死驱动不保证可恢复，可通过 logcat 诊断。

## 历史验收及范围

以下是单页改版之前的记录：61 项单测、Lint／构建通过；Android 15 验证四方向已知人脸／空白帧、当时十一主题独立设置、实际相机帧、暂停／恢复和退出全屏亮度还原，共享 248 项数字检查与历史回归也通过。它不代表当前 mycompat 仍有十一主题。

后续 Android 15 验证非方形样本四方向、七次实际旋转后持续分析。在关闭插电唤醒覆盖、屏幕超时 15 秒的 75 秒运行中，三次主动停止流均自动恢复，优先复用已成功的前置相机；窗口始终常亮，测试后恢复原电源设置。

上述为故障恢复覆盖，不是 YOGA Tablet 2-1050F / Android 5.0.1 长时间认证。该设备未连接；旧硬件和实际人脸／光照差异仍需单独验收。
