// "Add remaining branches with * import" "true"
// WITH_RUNTIME
import Foo.*

enum class Foo {
    A, B, C
}

enum class Bar {
    A, B, C
}

class Test {
    fun foo(e: Foo) {
        when (e) {
            A -> TODO()
            B -> TODO()
            C -> TODO()
        }
    }
    fun bar(e: Bar) {
        when<caret> (e) {
        }
    }
}
