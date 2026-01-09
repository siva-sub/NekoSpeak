package com.nekospeak.tts.engine.misaki

object Num2Words {
    private val ONES = arrayOf(
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )

    private val TENS = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    )

    private val THOUSANDS = arrayOf(
        "", "thousand", "million", "billion", "trillion", "quadrillion", "quintillion"
    )

    fun convert(number: Double): String {
        return convert(number.toLong())
    }
    
    fun convert(number: Int): String {
        return convert(number.toLong())
    }

    fun convert(number: Long): String {
        if (number == 0L) return "zero"

        var i = 0
        var words = ""
        var num = number

        if (number < 0) {
            num = -number // Handle negative
            words += "minus "
        }

        while (num > 0) {
            if (num % 1000 != 0L) {
                words = helper(num % 1000) + THOUSANDS[i] + " " + words
            }
            num /= 1000
            i++
        }

        return words.trim()
    }

    private fun helper(num: Long): String {
        return when {
            num == 0L -> ""
            num < 20 -> ONES[num.toInt()] + " "
            num < 100 -> TENS[num.toInt() / 10] + " " + helper(num % 10)
            else -> ONES[num.toInt() / 100] + " hundred " + helper(num % 100)
        }
    }
}
