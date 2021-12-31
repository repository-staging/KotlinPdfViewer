package app.testing.kotlinpdfviewer.utils

import android.text.TextUtils
import java.math.RoundingMode
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.ParseException
import java.util.*

object Utils {
    fun parseFileSize(fileSize: Long): String {
        val kb: Double = (fileSize / 1000).toDouble()

        if (kb == 0.0) {
            return "$fileSize Bytes"
        }

        val format = DecimalFormat("#.##")
        format.roundingMode = RoundingMode.CEILING

        if (kb < 1000) {
            return format.format(kb) + " kB (" + fileSize + " Bytes)"
        }
        return format.format(kb / 1000) + " MB (" + fileSize + " Bytes)"
    }

    // Parse date as per PDF spec (complies with PDF v1.4 to v1.7)
    @Throws(ParseException::class)
    fun parseDate(dateToParse: String): String? {
        var date = dateToParse
        var position = 0

        // D: prefix is optional for PDF < v1.7; required for PDF v1.7
        if (!date.startsWith("D:")) {
            date = "D:$date"
        }
        if (date.length < 6 || date.length > 23) {
            throw ParseException("Invalid datetime length", position)
        }
        val calendar = Calendar.getInstance()
        val currentYear = calendar[Calendar.YEAR]
        var year: Int

        // Year is required
        position += 2
        var field = date.substring(position, 6)
        if (!TextUtils.isDigitsOnly(field)) {
            throw ParseException("Invalid year", position)
        }

        year = Integer.valueOf(field)
        if (year > currentYear) {
            year = currentYear
        }
        position += 4

        // Default value for month and day shall be 1 (calendar month starts at 0 in Java 7),
        // all others default to 0
        var month = 0
        var day = 1
        var hours = 0
        var minutes = 0
        var seconds = 0

        // All succeeding fields are optional, but each preceding field must be present
        if (date.length > 8) {
            field = date.substring(position, 8)
            if (!TextUtils.isDigitsOnly(field)) {
                throw ParseException("Invalid month", position)
            }
            month = Integer.valueOf(field) - 1
            if (month > 11) {
                throw ParseException("Invalid month", position)
            }
            position += 2
        }
        if (date.length > 10) {
            field = date.substring(8, 10)
            if (!TextUtils.isDigitsOnly(field)) {
                throw ParseException("Invalid day", position)
            }
            day = Integer.valueOf(field)
            if (day > 31) {
                throw ParseException("Invalid day", position)
            }
            position += 2
        }
        if (date.length > 12) {
            field = date.substring(10, 12)
            if (!TextUtils.isDigitsOnly(field)) {
                throw ParseException("Invalid hours", position)
            }
            hours = Integer.valueOf(field)
            if (hours > 23) {
                throw ParseException("Invalid hours", position)
            }
            position += 2
        }
        if (date.length > 14) {
            field = date.substring(12, 14)
            if (!TextUtils.isDigitsOnly(field)) {
                throw ParseException("Invalid minutes", position)
            }
            minutes = Integer.valueOf(field)
            if (minutes > 59) {
                throw ParseException("Invalid minutes", position)
            }
            position += 2
        }
        if (date.length > 16) {
            field = date.substring(14, 16)
            if (!TextUtils.isDigitsOnly(field)) {
                throw ParseException("Invalid seconds", position)
            }
            seconds = Integer.valueOf(field)
            if (seconds > 59) {
                throw ParseException("Invalid seconds", position)
            }
            position += 2
        }
        if (date.length > position) {
            var offsetHours = 0
            var offsetMinutes = 0
            val utRel = date[position]
            if (utRel != '\u002D' && utRel != '\u002B' && utRel != '\u005A') {
                throw ParseException("Invalid UT relation", position)
            }
            position++
            if (date.length > position + 2) {
                field = date.substring(position, position + 2)
                if (!TextUtils.isDigitsOnly(field)) {
                    throw ParseException("Invalid UTC offset hours", position)
                }
                offsetHours = field.toInt()
                val offsetHoursMinutes = offsetHours * 100 + offsetMinutes

                // Validate UTC offset (UTC-12:00 to UTC+14:00)
                if (utRel == '\u002D' && offsetHoursMinutes > 1200 ||
                    utRel == '\u002B' && offsetHoursMinutes > 1400
                ) {
                    throw ParseException("Invalid UTC offset hours", position)
                }
                position += 2

                // Apostrophe shall succeed HH and precede mm
                if (date[position] != '\'') {
                    throw ParseException("Expected apostrophe", position)
                }
                position++
                if (date.length > position + 2) {
                    field = date.substring(position, position + 2)
                    if (!TextUtils.isDigitsOnly(field)) {
                        throw ParseException("Invalid UTC offset minutes", position)
                    }
                    offsetMinutes = field.toInt()
                    if (offsetMinutes > 59) {
                        throw ParseException("Invalid UTC offset minutes", position)
                    }
                    position += 2
                }

                // Apostrophe shall succeed mm
                if (date[position] != '\'') {
                    throw ParseException("Expected apostrophe", position)
                }
            }
            when (utRel) {
                '\u002D' -> {
                    hours -= offsetHours
                    minutes -= offsetMinutes
                }
                '\u002B' -> {
                    hours += offsetHours
                    minutes += offsetMinutes
                }
                else -> {}
            }
        }
        calendar[year, month, day, hours, minutes] = seconds
        return DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.LONG)
            .format(calendar.time)
    }


}