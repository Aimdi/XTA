import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import kotlin.system.exitProcess

/**
 * Lists the `@Test` methods JUnit never runs: JUnit Jupiter passes over a test
 * method that returns a value, silently. In Kotlin that is a test written
 * `fun x() = runBlocking { ... }` whose last line is a value, such as
 * `assertNotNull(...)`, `assertFailsWith { ... }` or `assertIs<T>(...)`.
 *
 *   java -cp <this, the tests and their classpath> TestReturnsKt <test classes dir>
 *
 * Exits 1 when it finds one, printing each.
 */
fun main(args: Array<String>) {
    val dir = File(args.single())
    val loader = Thread.currentThread().contextClassLoader ?: URLClassLoader.getSystemClassLoader()
    @Suppress("UNCHECKED_CAST")
    val test = Class.forName("org.junit.jupiter.api.Test", false, loader) as Class<out Annotation>
    val skipped = dir.walk()
        .filter { it.isFile && it.extension == "class" && '$' !in it.name }
        .mapNotNull { file -> runCatching { Class.forName(className(dir, file), false, loader) }.getOrNull() }
        .flatMap { type -> methodsOf(type).filter { it.isAnnotationPresent(test) && it.returnType != Void.TYPE } }
        .map { "NOT RUN BY JUNIT: ${it.declaringClass.name}.${it.name} returns ${it.returnType.simpleName}" }
        .sorted()
        .toList()
    skipped.forEach(::println)
    if (skipped.isNotEmpty()) {
        println("${skipped.size} test(s) return a value: end each with an assertion that returns nothing, or Unit")
        exitProcess(1)
    }
}

private fun className(root: File, file: File) = file.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.')

private fun methodsOf(type: Class<*>): List<Method> = runCatching { type.declaredMethods.toList() }.getOrDefault(emptyList())
