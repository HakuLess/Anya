# Anya - 漫画阅读应用

Anya是一款专为Android平台设计的漫画阅读应用，支持EPUB格式，提供真实的翻页效果和完整的书架管理功能。

## 功能特性

### 📚 书架管理
- **书籍导入**: 支持从本地存储导入EPUB格式的漫画文件
- **分类管理**: 可创建、编辑、删除书籍分类，支持自定义颜色和图标
- **收藏功能**: 支持收藏喜欢的书籍，快速访问
- **阅读进度**: 自动记录和显示每本书的阅读进度

### 📖 阅读体验
- **EPUB支持**: 完整支持EPUB格式，自动解析封面和页面
- **真实翻页**: 模拟真实书籍的翻页动画效果
- **手势操作**: 支持左右滑动翻页，触摸友好
- **阅读设置**: 可调节阅读参数，个性化阅读体验

### 🎨 用户界面
- **Material Design**: 采用最新的Material Design 3设计语言
- **响应式布局**: 适配不同屏幕尺寸，支持横竖屏切换
- **夜间模式**: 支持深色主题，保护眼睛
- **流畅动画**: 丰富的过渡动画，提升用户体验

## 技术架构

### 核心技术
- **Kotlin**: 使用Kotlin语言开发，代码简洁高效
- **MVVM架构**: 采用Model-View-ViewModel架构模式
- **Room数据库**: 使用Room持久化库管理本地数据
- **Navigation组件**: 使用Navigation组件管理页面导航

### 主要依赖
- **EPUB解析**: epublib-core 4.0
- **图片加载**: Glide 4.16.0
- **翻页组件**: ViewPager2
- **协程支持**: Kotlin Coroutines
- **生命周期**: Android Lifecycle Components

## 项目结构

```
Anya/
├── app/
│   ├── src/main/
│   │   ├── java/com/haku/anya/
│   │   │   ├── data/           # 数据模型和数据库
│   │   │   ├── epub/           # EPUB解析器
│   │   │   ├── repository/     # 数据仓库
│   │   │   ├── ui/             # 用户界面
│   │   │   │   ├── bookshelf/  # 书架页面
│   │   │   │   └── reader/     # 阅读器
│   │   │   └── MainActivity.kt # 主Activity
│   │   └── res/                # 资源文件
│   └── build.gradle.kts        # 应用级构建配置
├── gradle/                      # Gradle配置
└── build.gradle.kts            # 项目级构建配置
```

## 安装说明

### 环境要求
- Android Studio Hedgehog | 2023.1.1 或更高版本
- Android SDK 24 (API Level 24) 或更高
- Kotlin 2.0.21 或更高

### 构建步骤
1. 克隆项目到本地
2. 在Android Studio中打开项目
3. 同步Gradle依赖
4. 连接Android设备或启动模拟器
5. 点击运行按钮构建并安装应用

## 使用说明

### 添加书籍
1. 在书架页面点击右下角的"+"按钮
2. 选择EPUB格式的漫画文件
3. 应用会自动解析文件并添加到书架

### 创建分类
1. 点击右下角的分类按钮
2. 输入分类名称和选择颜色
3. 将书籍拖拽到对应分类

### 阅读漫画
1. 点击书架中的书籍封面
2. 使用左右滑动或底部按钮翻页
3. 阅读进度会自动保存

### 管理书架
- **长按书籍**: 显示编辑、删除、收藏等选项
- **点击分类**: 筛选显示对应分类的书籍
- **收藏书籍**: 在收藏分类中快速访问

## 开发计划

### 近期计划
- [ ] 支持更多漫画格式 (CBZ, CBR)
- [ ] 添加书签功能
- [ ] 实现云同步
- [ ] 添加阅读统计

### 长期计划
- [ ] 支持在线漫画源
- [ ] 添加社区功能
- [ ] 实现AI推荐
- [ ] 多平台支持

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

## 许可证

本项目采用MIT许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 联系方式

- 项目主页: [GitHub Repository](https://github.com/yourusername/anya)
- 问题反馈: [Issues](https://github.com/yourusername/anya/issues)
- 功能建议: [Discussions](https://github.com/yourusername/anya/discussions)

## 致谢

感谢所有为这个项目做出贡献的开发者和用户！

---

**Anya** - 让阅读更美好 📚✨
