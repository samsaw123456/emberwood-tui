package emberwood

import com.williamcallahan.tui4j.compat.bubbletea.BlurMessage
import com.williamcallahan.tui4j.compat.bubbletea.Message
import com.williamcallahan.tui4j.compat.bubbletea.Model
import com.williamcallahan.tui4j.compat.bubbletea.Program
import com.williamcallahan.tui4j.compat.bubbletea.ProgramOption
import com.williamcallahan.tui4j.compat.bubbletea.SuspendMessage
import org.jline.nativ.CLibrary
import org.jline.utils.OSUtils
import kotlin.system.exitProcess

internal fun terminalMessage(message: Message): Message = if (message is SuspendMessage) BlurMessage() else message

internal fun runTerminal(model: Model) {
    // JLine's Attributes conversion omits native baud/control bits. Preserve
    // the entire termios structure before its terminal setup changes anything.
    val original = CLibrary.Termios()
    val active = CLibrary.Termios()
    if (CLibrary.tcgetattr(0, original) != 0 || CLibrary.tcgetattr(0, active) != 0) {
        System.err.println("Cannot read the terminal settings.")
        exitProcess(2)
    }
    // Do not truncate a repaint when the terminal generates SIGINT/SIGTSTP.
    // Flushing an active Linux PTY write can strand tui4j's renderer lock.
    // NOFLSH values come from Linux asm-generic/termbits.h and Darwin sys/termios.h.
    val noFlush = if (OSUtils.IS_OSX) 0x80000000L else 0x80L
    active.c_lflag = active.c_lflag or noFlush
    try {
        if (CLibrary.tcsetattr(0, CLibrary.TCSANOW, active) != 0) {
            System.err.println("Cannot prepare the terminal settings.")
            exitProcess(2)
        }
        Program(
            model,
            // tui4j 0.3.5 stops reading on TSTP without handing control back to
            // the shell. Use a recoverable pause, as for focus loss, instead.
            ProgramOption.withFilter { _, message -> terminalMessage(message) },
        ).withAltScreen().withReportFocus().run()
    } finally {
        if (CLibrary.tcsetattr(0, CLibrary.TCSANOW, original) != 0) {
            System.err.println("Could not restore terminal settings. Run 'stty sane' in your shell.")
            exitProcess(1)
        }
    }
}
