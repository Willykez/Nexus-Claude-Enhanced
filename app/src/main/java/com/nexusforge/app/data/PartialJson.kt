package com.nexusforge.app.data

/**
 * Reads a string field out of a JSON object that is STILL BEING STREAMED, e.g. the arguments of
 * a write_file call arriving token by token: `{"path":"A.kt","content":"fun main() {\n  prin`.
 *
 * Returns whatever has arrived so far, fully unescaped (\n, \", \uXXXX ...), or null if the field
 * hasn't started yet. An incomplete trailing escape is dropped instead of being shown half-decoded.
 * This is what lets the chat show code live while the model is writing it.
 */
object PartialJson {

    fun string(json: String, field: String): String? {
        val key = "\"$field\""
        var i = json.indexOf(key)
        while (i >= 0) {
            var j = i + key.length
            while (j < json.length && json[j].isWhitespace()) j++
            if (j < json.length && json[j] == ':') {
                j++
                while (j < json.length && json[j].isWhitespace()) j++
                if (j >= json.length || json[j] != '"') return null
                return decode(json, j + 1)
            }
            i = json.indexOf(key, i + key.length)
        }
        return null
    }

    private fun decode(s: String, start: Int): String {
        val out = StringBuilder()
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (c == '"') return out.toString()
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            if (i + 1 >= s.length) return out.toString()
            when (val e = s[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'u' -> {
                    if (i + 6 > s.length) return out.toString()
                    val cp = s.substring(i + 2, i + 6).toIntOrNull(16)
                    if (cp != null) out.append(cp.toChar())
                    i += 6
                    continue
                }
                else -> out.append(e) // \" \\ \/ and anything unknown
            }
            i += 2
        }
        return out.toString()
    }
}
