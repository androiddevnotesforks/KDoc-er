package com.kdocer.generator

import com.intellij.psi.PsiNameIdentifierOwner
import java.util.regex.Pattern

interface KDocGenerator {

    companion object {
        const val LF = "\n"
    }

    fun generate(): String

    fun toParamsKdoc(keyword: String = "@param", params: List<PsiNameIdentifierOwner>): String =
        params.map { "$keyword ${it.name}" }
            .joinToString(LF, transform = { "* $it" })

    fun StringBuilder.appendLine(text: String): StringBuilder = append(text).append(LF)


    /**
     * @param name the name
     * @return the result of converting the name to a phrase
     */
    private fun nameToPhraseAllUpperCase(name: String): String {
        return name.split(Pattern.compile("_"))
            .joinToString(separator = " ") { s: String -> s.toTitleCase() }
    }

    fun nameToPhrase(name: String): String {
        if (name.isAllUpperCase()) {
            return nameToPhraseAllUpperCase(name)
        }
        val array = name.split(Pattern.compile("(?<=[a-zA-Z])(?=[A-Z])"))
        val builder = StringBuilder()
        array.forEach {
            builder.append(it.toLowerCase())
            builder.append(" ")
        }
        val phrase = builder.toString()
        return phrase.capitalize()
    }
}

/**
 * Converts a String to Title Case
 * @return the String in Title Case
 */
private fun String.toTitleCase(): String {
    val sb = StringBuilder()
    this.forEachIndexed { i: Int, c: Char ->
        sb.append(if (i == 0) {c.toUpperCase()} else {c.toLowerCase()})
    }
    return sb.toString()
}

/**
 * Checks whether a String is all uppercase, by iterating through all the characters
 * @return whether the String is all uppercase
 */
private fun String.isAllUpperCase(): Boolean {
    this.forEach {
        if (it.isLowerCase()) {
            return false
        }
    }
    return true
}

