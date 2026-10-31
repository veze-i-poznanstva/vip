package vip

/**
 * A contributed string made safe to print on one line: every control character, newlines
 * included, becomes a space. A label in a data file can therefore neither inject a line that a
 * CI runner would read as a command (such as GitHub Actions' `::` syntax) nor send terminal
 * escape sequences to the reader's screen.
 */
fun String.printable(): String = replace(CONTROL_CHARACTERS, " ")

private val CONTROL_CHARACTERS = Regex("\\p{Cntrl}+")
