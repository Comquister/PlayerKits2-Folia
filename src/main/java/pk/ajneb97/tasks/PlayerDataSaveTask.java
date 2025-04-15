package pk.ajneb97.tasks;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import pk.ajneb97.PlayerKits2;

public class PlayerDataSaveTask {

	private final PlayerKits2 plugin;
	private final FoliaLib foliaLib;
	private WrappedTask task;
	private boolean end;

	public PlayerDataSaveTask(PlayerKits2 plugin) {
		this.plugin = plugin;
		this.foliaLib = new FoliaLib(plugin);
		this.end = false;
	}

	public void end() {
		this.end = true;
		if (task != null && !task.isCancelled()) {
			task.cancel();
		}
	}

	public void start(int seconds) {
		long ticks = seconds * 20L;

		task = foliaLib.getScheduler().runTimerAsync(() -> {
			if (end) {
				task.cancel(); // cancela a task se necessário
			} else {
				execute();
			}
		}, 1L, ticks);
	}

	public void execute() {
		plugin.getConfigsManager().getPlayersConfigManager().saveConfigs();
	}
}
