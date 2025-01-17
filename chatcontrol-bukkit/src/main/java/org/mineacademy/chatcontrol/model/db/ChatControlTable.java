package org.mineacademy.chatcontrol.model.db;

import org.mineacademy.fo.database.Row;
import org.mineacademy.fo.database.SimpleDatabase;
import org.mineacademy.fo.database.SimpleDatabase.TableCreator;
import org.mineacademy.fo.database.Table;

import lombok.Getter;

@Getter
public enum ChatControlTable implements Table {

	/**
	 * The table storing player data
	 */
	PLAYERS("players", "ChatControl", PlayerCache.class) {

		@Override
		public void onTableCreate(final TableCreator creator) {
			creator
					.addNotNull("UUID", "varchar(64)")
					.add("Name", "text")
					.add("Nick", "text")
					.add("Data", "longtext")
					.addDefault("LastModified", "bigint(20)", "NULL")
					.setPrimaryColumn("UUID");
		}
	},

	LOGS("logs", "ChatControl_Log", Log.class) {

		@Override
		public void onTableCreate(final TableCreator creator) {
			creator
					.addAutoIncrement("Id", "int")
					.add("Server", "text")
					.addDefault("Date", "datetime", "NULL")
					.add("Type", "text")
					.add("Sender", "text")
					.add("Receiver", "text")
					.add("Content", "longtext")
					.add("ChannelName", "text")
					.add("RuleName", "text")
					.add("RuleGroupName", "text")
					.setPrimaryColumn("Id");
		}
	},

	MAIL("mails", "ChatControl_Mail", Mail.class) {

		@Override
		public void onTableCreate(final TableCreator creator) {
			creator
					.addNotNull("UUID", "varchar(64)")
					.add("Sender", "varchar(64)")
					.addDefault("Sender_Deleted", "tinyint(1)", "0")
					.add("Recipients", "longtext") // Serialized List<Recipient>
					.add("Body", "longtext") // Serialized Book object
					.addDefault("Send_Date", "bigint(20)", "0") // Send date as long
					.addDefault("Auto_Reply", "tinyint(1)", "0")
					.setPrimaryColumn("UUID");
		}
	},

	SETTINGS("settings", "ChatControl_Settings", ServerSettings.class) {

		@Override
		public void onTableCreate(final TableCreator creator) {
			creator
					.addNotNull("Server", "varchar(64)")
					.add("Unmute_Time", "bigint(20)")
					.setPrimaryColumn("Server");
		}
	};

	private final String key;
	private final String name;
	private final Class<? extends Row> rowClass;

	ChatControlTable(final String key, final String name, final Class<? extends Row> rowClass) {
		this.key = key;
		this.name = name;
		this.rowClass = rowClass;
	}

	@Override
	public SimpleDatabase getDatabase() {
		return Database.getInstance();
	}
}