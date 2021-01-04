package kdocformatter.cli

import kdocformatter.KDocFormattingOptions
import java.io.File

/**
 * Options for configuring whole files or directories. The
 * [formattingOptions] property specifies the KDoc specific formatting
 * options; the rest of the options are related to how to (and whether
 * to) process files.
 */
class KDocFileFormattingOptions {
    var dryRun: Boolean = false
    var quiet: Boolean = false
    var gitPath: String = ""
    var filter = RangeFilter() // default accepts all
    var files = listOf<File>()
    var formattingOptions: KDocFormattingOptions = KDocFormattingOptions()

    companion object {
        fun parse(args: Array<String>): KDocFileFormattingOptions {
            val options = KDocFileFormattingOptions()
            var i = 0
            val files = mutableListOf<File>()
            options.files = files
            val rangeLines = mutableListOf<String>()
            var overlapsGitHead = false
            var overlapsGitStaged = false

            fun parseInt(s: String): Int = s.toIntOrNull() ?: error("$s is not a number")

            while (i < args.size) {
                val arg = args[i]
                i++
                when {
                    arg == "--help" || arg == "-help" || arg == "-h" -> println(usage())
                    arg == "--max-line-width" || arg == "--line-width" || arg == "--right-margin" ->
                        options.formattingOptions.maxLineWidth = parseInt(args[i++])
                    arg.startsWith("--max-line-width=") ->
                        options.formattingOptions.maxLineWidth = parseInt(arg.substring("--max-line-width=".length))
                    arg == "--max-comment-width" ->
                        options.formattingOptions.maxCommentWidth = parseInt(args[i++])
                    arg.startsWith("--max-comment-width=") ->
                        options.formattingOptions.maxCommentWidth = parseInt(arg.substring("--max-comment-width=".length))
                    arg.startsWith("--single-line-comments=collapse") ->
                        options.formattingOptions.collapseSingleLine = true
                    arg.startsWith("--single-line-comments=expand") ->
                        options.formattingOptions.collapseSingleLine = false
                    arg.startsWith("--single-line-comments=") ->
                        error("Only `collapse` and `expand` are supported for --single-line-comments")
                    arg == "--overlaps-git-changes=HEAD" -> overlapsGitHead = true
                    arg == "--overlaps-git-changes=staged" -> overlapsGitStaged = true
                    arg.startsWith("--overlaps-git-changes=") ->
                        error("Only `HEAD` and `staged` are supported for --overlaps-git-changes")
                    arg == "--lines" || arg == "--line" -> rangeLines.add(args[i++])
                    arg.startsWith("--lines=") -> rangeLines.add(arg.substring("--lines=".length))
                    arg == "--dry-run" || arg == "-n" -> options.dryRun = true
                    arg == "--quiet" || arg == "-q" -> options.quiet = true
                    arg == "--git-path" -> options.gitPath = args[i++]
                    arg.startsWith("--git-path=") -> options.gitPath = arg.substring("--git-path=".length)
                    else -> {
                        val paths =
                            if (arg.startsWith("@")) {
                                val f = File(arg.substring(1))
                                if (!f.exists()) {
                                    error("$f does not exist")
                                }
                                f.readText().split('\n').toList()
                            } else {
                                listOf(arg)
                            }
                        for (path in paths) {
                            if (path.isBlank()) {
                                continue
                            }
                            val file = File(arg.trim())
                            if (!file.exists()) {
                                error("$file does not exist")
                            }
                            files.add(file)
                        }
                    }
                }
            }

            if ((overlapsGitHead || overlapsGitStaged) && files.isNotEmpty()) {
                // Delayed initialization because the git path and the paths to the repository
                // is typically specified after this flag
                val filter = GitRangeFilter.create(options.gitPath, files.first(), overlapsGitStaged)
                if (filter == null) {
                    error("Creating git range filter failed")
                } else if (filter.empty) {
                    if (!options.quiet) {
                        println(
                            "No changes to Kotlin files found in ${
                            if (overlapsGitStaged) "the staged files" else "HEAD"
                            }"
                        )
                    }
                }
                options.filter = filter
            } else if (rangeLines.isNotEmpty()) {
                if (files.size != 1 || !files[0].isFile) {
                    error("The --lines option can only be used with a single file")
                } else {
                    options.filter = LineRangeFilter.fromRangeStrings(files[0], rangeLines)
                }
            }

            return options
        }

        fun usage(): String {
            return """
            Usage: kdoc-formatter [options] file(s)
            
            Options:
              --max-line-width=<n>
                Sets the length of lines. Defaults to 72. 
              --max-comment-width=<n>
                Sets the maximum width of comments. This is helpful in a codebase
                with large line lengths, such as 140 in the IntelliJ codebase. Here,
                you don't want to limit the formatter maximum line width since
                indented code still needs to be properly formatted, but you also
                don't want comments to span 100+ characters, since that's less
                readable. By default this option is not set.
              --single-line-comments=<collapse | expand>
                With `collapse`, turns multi-line comments into a single line if it
                fits, and with `expand` it will always format commands with /** and
                */ on their own lines. The default is `collapse`.
              --overlaps-git-changes=<HEAD | staged>
                If git is on the path, and the command is invoked in a git
                repository, kdoc-formatter will invoke git to find the changes either
                in the HEAD commit or in the staged files, and will format only the
                KDoc comments that overlap these changes.
              --lines <start:end>, --line <start>
                Line range(s) to format, like 5:10 (1-based; default is all). Can be
                specified multiple times.
              --dry-run, -n
                Prints the paths of the files whose contents would change if the
                formatter were run normally.
              --quiet, -q
                Quiet mode
              --help, -help, -h
                Print this usage statement.
              @<filename>
                Read filenames from file.
            
            kdoc-formatter: Version 1.1
            https://github.com/tnorbye/kdoc-formatter        
            """.trimIndent()
        }
    }
}
