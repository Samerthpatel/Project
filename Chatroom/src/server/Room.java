package server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Room implements AutoCloseable {
	private static SocketServer server; // used to refer to accessible server functions
	private String name;
	private final static Logger log = Logger.getLogger(Room.class.getName());
	private Random generator = new Random();

	// Commands
	private final static String COMMAND_TRIGGER = "/";
	private final static String AT_SYMBOL = "@";
	private final static String CREATE_ROOM = "createroom";
	private final static String JOIN_ROOM = "joinroom";
	private final static String ROLL = "roll";
	private final static String FLIP = "flip";
	private final static String COLOR = "color";
	private final static String MUTE = "mute";
	private final static String UNMUTE = "unmute";

	public Room(String name) {
		this.name = name;
	}

	public static void setServer(SocketServer server) {
		Room.server = server;
	}

	public String getName() {
		return name;
	}

	private List<ServerThread> clients = new ArrayList<ServerThread>();

	protected synchronized void addClient(ServerThread client) {
		client.setCurrentRoom(this);
		client.loadMutedUsers();
		if (clients.indexOf(client) > -1) {
			log.log(Level.INFO, "Attempting to add a client that already exists");
		} else {
			clients.add(client);
			if (client.getClientName() != null) {
				client.sendClearList();
				sendConnectionStatus(client, true, "joined the room " + getName());
				updateClientList(client);
			}
		}
	}

	private void updateClientList(ServerThread client) {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread c = iter.next();
			if (c != client) {
				client.sendConnectionStatus(c.getClientName(), true, null);
			}
		}
	}

	protected synchronized void removeClient(ServerThread client) {
		clients.remove(client);
		if (clients.size() > 0) {
			// sendMessage(client, "left the room");
			sendConnectionStatus(client, false, "left the room " + getName());
		} else {
			cleanupEmptyRoom();
		}
	}

	private void cleanupEmptyRoom() {
		// If name is null it's already been closed. And don't close the Lobby
		if (name == null || name.equalsIgnoreCase(SocketServer.LOBBY)) {
			return;
		}
		try {
			log.log(Level.INFO, "Closing empty room: " + name);
			close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void joinRoom(String room, ServerThread client) {
		server.joinRoom(room, client);
	}

	protected void createRoom(String room, ServerThread client) {
		if (server.createNewRoom(room)) {
			joinRoom(room, client);
		}
	}

	protected void joinLobby(ServerThread client) {
		server.joinLobby(client);
	}

	// Function to process bold, italics, and underline
	private String textEffects(String message) {
		// Bold
		if (message.contains("-")) {
			message = message.replaceAll("\\-\\b", "<b>").replaceAll("\\b\\-", "</b>");
		}
		// Italics
		if (message.contains("*")) {
			message = message.replaceAll("\\*\\b", "<i>").replaceAll("\\b\\*", "</i>");
		}
		// Underline
		if (message.contains("_")) {
			message = message.replaceAll("\\b_", "<u>").replaceAll("_\\b", "</u>");
		}
		return message;
	}

	/***
	 * Helper function to process messages to trigger different functionality.
	 * 
	 * @param message The original message being sent
	 * @param client  The sender of the message (since they'll be the ones
	 *                triggering the actions)
	 */
	private String processCommands(String message, ServerThread client) {
		// boolean wasCommand = false;
		String response = null;
		try {
			if (message.indexOf(COMMAND_TRIGGER) > -1) {
				String[] comm = message.split(COMMAND_TRIGGER);
				log.log(Level.INFO, message);
				String part1 = comm[1];
				String[] comm2 = part1.split(" ");
				String command = comm2[0];
				if (command != null) {
					command = command.toLowerCase();
				}
				String roomName;
				switch (command) {
				case CREATE_ROOM:
					roomName = comm2[1];
					if (server.createNewRoom(roomName)) {
						joinRoom(roomName, client);
					}
					// wasCommand = true;
					break;
				case JOIN_ROOM:
					roomName = comm2[1];
					joinRoom(roomName, client);
					// wasCommand = true;
					break;
				case ROLL:
					response = "<i><b style=\"color: red;\">Rolled: </b></i>"
							+ Integer.toString(generator.nextInt(6) + 1);
					// wasCommand = true;
					break;
				case FLIP:
					String[] coin = { "Heads", "Tails" };
					response = "<i><b style=\"color: green;\">Flipped: </b></i>" + coin[generator.nextInt(coin.length)];
					// wasCommand = true;
					break;
				case COLOR:
					response = "<span style=\"color: " + comm2[1] + ";\">"
							+ textEffects(String.join(" ", Arrays.copyOfRange(comm2, 2, comm2.length))) + "</span>";
					// wasCommand = true;
					break;
				case MUTE:
					String username_mute = comm2[1].split(AT_SYMBOL)[1];
					if (username_mute != null) {
						username_mute = username_mute.toLowerCase();
					}
					if (!client.isMuted(username_mute)) {
						client.muted.add(username_mute);
						client.saveMutedUsers();
						sendPrivateMessage(client, username_mute, "<i>muted you.</i>", false);
					}
					// wasCommand = true;
					break;
				case UNMUTE:
					String username_unmute = comm2[1].split(AT_SYMBOL)[1];
					if (username_unmute != null) {
						username_unmute = username_unmute.toLowerCase();
					}
					if (client.isMuted(username_unmute)) {
						client.muted.remove(username_unmute);
						client.saveMutedUsers();
						sendPrivateMessage(client, username_unmute, "<i>unmuted you.</i>", false);
					}
					// wasCommand = true;
					break;
				default:
					response = textEffects(message);
					break;
				}
			} else if (message.indexOf(AT_SYMBOL) > -1) {
				String[] comm = message.split(AT_SYMBOL);
				log.log(Level.INFO, message);
				String part1 = comm[1];
				String[] comm2 = part1.split(" ");
				String username = comm2[0];
				if (username != null) {
					username = username.toLowerCase();
				}
				message = textEffects(String.join(" ", Arrays.copyOfRange(comm2, 1, comm2.length)));
				message = "<i>(Private: " + username + ")</i> " + message;
				sendPrivateMessage(client, username, message, true);
			} else {
				response = textEffects(message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// return wasCommand;
		return response;
	}

	// TODO changed from string to ServerThread
	protected void sendConnectionStatus(ServerThread client, boolean isConnect, String message) {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread c = iter.next();
			boolean messageSent = c.sendConnectionStatus(client.getClientName(), isConnect, message);
			if (!messageSent) {
				iter.remove();
				log.log(Level.INFO, "Removed client " + c.getId());
			}
		}
	}

	protected void sendPrivateMessage(ServerThread sender, String receiver, String message, boolean self) {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread client = iter.next();
			if (!client.isMuted(sender.getClientName().toLowerCase()) || !self) {
				if (client.getClientName().equalsIgnoreCase(receiver)
						|| (sender.getClientName() == client.getClientName() && self)) {
					client.send(sender.getClientName(), message);
				}
			}
		}
	}

	/***
	 * Takes a sender and a message and broadcasts the message to all clients in
	 * this room. Client is mostly passed for command purposes but we can also use
	 * it to extract other client info.
	 * 
	 * @param sender  The client sending the message
	 * @param message The message to broadcast inside the room
	 */
	protected void sendMessage(ServerThread sender, String message) {
		log.log(Level.INFO, getName() + ": Sending message to " + clients.size() + " clients");
		String response = processCommands(message, sender);
		if (response == null) {
			// it was a command, don't broadcast
			return;
		}
		message = response;
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread client = iter.next();
			if (!client.isMuted(sender.getClientName().toLowerCase())) {
				boolean messageSent = client.send(sender.getClientName(), message);
				if (!messageSent) {
					iter.remove();
					log.log(Level.INFO, "Removed client " + client.getId());
				}
			}
		}
	}

	public List<String> getRooms(String room) {
		return server.getRooms(room);
	}

	/***
	 * Will attempt to migrate any remaining clients to the Lobby room. Will then
	 * set references to null and should be eligible for garbage collection
	 */
	@Override
	public void close() throws Exception {
		int clientCount = clients.size();
		if (clientCount > 0) {
			log.log(Level.INFO, "Migrating " + clients.size() + " to Lobby");
			Iterator<ServerThread> iter = clients.iterator();
			Room lobby = server.getLobby();
			while (iter.hasNext()) {
				ServerThread client = iter.next();
				lobby.addClient(client);
				iter.remove();
			}
			log.log(Level.INFO, "Done Migrating " + clients.size() + " to Lobby");
		}
		server.cleanupRoom(this);
		name = null;
		// should be eligible for garbage collection now
	}

}