package com.open.terminal.openterminal;

/**
 * @description: 程序启动类，一定要通过该主启动类去启动继承Application的主类，否则运行package之后打包出的
 * jar包运行时会报错：错误: 缺少 JavaFX 运行时组件, 需要使用该组件来运行此应用程序
 * 在pom文件中<mainClass> 也应该指向当前启动类
 *
 * 解释：
 * 在 pom.xml 的 maven-shade-plugin 中配置的 <mainClass> 指向了 Launcher，
 * 而 Launcher 里的代码直接引用了 OpenTerminalStarter（它继承自 Application），
 * JVM 在加载 Launcher 类时，可能会尝试去验证它引用的所有类（包括 OpenTerminalStarter）。
 * 由于 OpenTerminalStarter 继承自 Application，JVM 可能会立刻检查 JavaFX 运行时是否存在。
 *
 * Launcher 作为一个完全独立的入口，成功“欺骗”了 JVM，
 * 让它先加载了 Fat Jar 里的所有依赖库（包括 JavaFX），然后再去触碰那些需要继承 Application 的类。
 *
 * @author：dukelewis
 * @date: 2025/12/28
 * @Copyright： https://github.com/DukeLewis
 */
public class Launcher {
    public static void main(String[] args) {
        OpenTerminalStarter.main(args);
    }
}
