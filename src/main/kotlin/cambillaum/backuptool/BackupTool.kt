package cambillaum.backuptool

import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import java.io.*

fun main(args: Array<String>): Unit {
    val cmdLine = CmdLine()
    val parser = CmdLineParser(cmdLine)
    try {
        parser.parseArgument(args.toMutableList())
    } catch(e: CmdLineException) {
        e.printStackTrace()
        parser.printUsage(System.out)
    }
    cmdLine.validate()
    when(cmdLine.action) {
        CmdLineAction.Backup -> backup(cmdLine.source?:(throw IllegalStateException("source is null")), cmdLine.splitSize, cmdLine.target?:(throw IllegalStateException("target is null")))
        CmdLineAction.Restore -> restore(cmdLine.source?:(throw IllegalStateException("source is null")), cmdLine.target?:(throw IllegalStateException("target is null")))
    }
}

fun backup(source: String, splitSize: Long, target: String): Unit {
    val tarProcess = ProcessBuilder(listOf("tar", "-czf", "-", source)).start()
    val gpgProcess = ProcessBuilder(listOf("gpg", "--cipher-algo", "AES256", "--symmetric", "-")).start()
    val splitProcess = ProcessBuilder(listOf("split", "-b", splitSize.toString(), "-", target)).redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).start()
    pipe(gpgProcess, splitProcess)
    pipe(tarProcess, gpgProcess)
    splitProcess.waitFor()
}

fun restore(source: String, target: String): Unit {
    val files = File(source).listFiles().toList().map({ it.absolutePath }).sorted()
    val catProcess = ProcessBuilder(listOf("cat").plus(files)).start()
    val gpgProcess = ProcessBuilder(listOf("gpg")).start()
    val tarProcess = ProcessBuilder(listOf("tar", "-xzf", "-", "-C", target)).redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).start()
    pipe(gpgProcess, tarProcess)
    pipe(catProcess, gpgProcess)
    tarProcess.waitFor()
}

fun pipe(process1: Process, process2: Process): Unit {
    Thread() {
        val inputStream = process1.inputStream
        val outputStream = process2.outputStream
        BufferedInputStream(inputStream).use { inputStream ->
            BufferedOutputStream(outputStream).use { outputStream ->
                while(true) {
                    val read = inputStream.read()
                    if(read == -1) {
                        break
                    }
                    outputStream.write(read)
                }
            }
        }
    }.start()
}

enum class CmdLineAction {
    Backup, Restore;
}

class CmdLine() {
    @Option(name = "-a", usage = "-a (backup|restore), the action to perform", required = true)
    var action: CmdLineAction? = null
    @Option(name = "-s", usage = "-s sourceFolder, the source folder to backup or restore from", required = true)
    var source: String? = null
    @Option(name = "-t", usage = "-t (/.../target.tar.gz.gpg-|targetFolder), the backup file pattern or restore target folder", required = true)
    var target: String? = null
    @Option(name = "-z", usage = "-z 1000000000, the split size for backup", required = false)
    var splitSize: Long = 1000000000L

    override fun toString(): String = "CmdLine(action = $action, source=$source, target=$target, splitSize=$splitSize)"

    fun validate(): Unit {
        val sourceFolder = File(source)
        require(sourceFolder.exists(), { "$source does not exist" })
        require(sourceFolder.isDirectory, { "$source is not a folder" })
        require(sourceFolder.canRead(), { "$source cannot be read" })
        when(action) {
            CmdLineAction.Backup -> {
                val parentFolder = File(target).parentFile
                require(parentFolder.exists(), { "$parentFolder does not exist" })
                require(parentFolder.isDirectory, { "$parentFolder is not a folder" })
                require(parentFolder.canWrite(), { "$parentFolder cannot be written to" })
            }
            CmdLineAction.Restore -> {
                val targetFolder = File(target)
                require(targetFolder.exists(), { "$targetFolder does not exist" })
                require(targetFolder.isDirectory, { "$targetFolder is not a directory" })
                require(targetFolder.canWrite(), { "$targetFolder cannot be written to" })
            }
        }
    }
}