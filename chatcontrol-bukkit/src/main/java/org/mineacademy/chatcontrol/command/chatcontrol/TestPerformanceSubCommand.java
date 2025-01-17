package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.Arrays;
import java.util.List;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.fo.debug.LagCatcher;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public final class TestPerformanceSubCommand extends MainSubCommand {

	public TestPerformanceSubCommand() {
		super("testperf");

		this.setDescription("[Debug] Runs a format 500x and measures time taken.");
		this.setUsage("<" + Arrays.stream(Param.values()).map(Param::getKey).reduce((a, b) -> a + "/" + b).get() + "> <value>");
		this.setMinArguments(2);
		this.setPermission("chatcontrol.command.testperf");
	}

	@Override
	protected boolean showInHelp() {
		return false;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		final Param param = this.findEnum(Param.class, this.args[0]);
		final String value = this.args[1];
		final WrappedSender wrapped = WrappedSender.fromSender(this.getSender());

		if (param == Param.FORMAT) {
			final Format format = Format.parse(value);

			final long nano = System.nanoTime();

			for (int i = 0; i < 500; i++)
				format.build(wrapped);

			LagCatcher.took(nano, "building format");
		}
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		switch (this.args.length) {
			case 1:
				return this.completeLastWord(Param.values());

			case 2: {
				Param param = null;

				try {
					param = Param.valueOf(this.args[0].toUpperCase());
				} catch (final IllegalArgumentException ex) {
				}

				if (param == Param.FORMAT)
					return this.completeLastWord(Format.getFormats());
			}
		}

		return NO_COMPLETE;
	}

	@RequiredArgsConstructor
	enum Param {

		FORMAT("format");

		@Getter
		private final String key;

		@Override
		public String toString() {
			return this.key;
		}
	}
}
