package com.soreepeong.darknova.twitter;

import android.os.Parcel;
import android.os.Parcelable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.soreepeong.darknova.tools.StreamTools;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Twitter entities
 *
 * @author Soreepeong
 */
public class Entities implements Parcelable {
	@SuppressWarnings("unused")
	public static final Parcelable.Creator<Entities> CREATOR = new Parcelable.Creator<Entities>() {
		@Override
		public Entities createFromParcel(Parcel in) {
			return new Entities(in);
		}

		@Override
		public Entities[] newArray(int size) {
			return new Entities[size];
		}
	};
	public final ArrayList<Entity> entities = new ArrayList<>();

	public Entities(JsonParser parser) throws IOException {
		String key;
		while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
			key = parser.getCurrentName();
			parser.nextToken();
			while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_ARRAY) {
				switch (key) {
					case "media": entities.add(new MediaEntity(parser)); break;
					case "urls": entities.add(new UrlEntity(parser)); break;
					case "user_mentions": entities.add(new MentionsEntity(parser)); break;
					case "hashtags": entities.add(new HashtagEntity(parser)); break;
					case "symbols": entities.add(new SymbolEntity(parser)); break;
				}
			}
		}
	}

	protected Entities(Parcel in) {
		in.readList(entities, Entity.class.getClassLoader());
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeList(entities);
	}

	public static class Entity implements Parcelable {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<Entity> CREATOR = new Parcelable.Creator<Entity>() {
			@Override
			public Entity createFromParcel(Parcel in) {
				return new Entity(in);
			}

			@Override
			public Entity[] newArray(int size) {
				return new Entity[size];
			}
		};
		public int indice_left;
		public int indice_right;

		public Entity(JsonParser parser) throws IOException {
			String key;
			while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
				key = parser.getCurrentName();
				parser.nextToken();
				if (parser.getCurrentToken() == JsonToken.VALUE_NULL) continue;
				if (key.equals("indices")) {
					parser.nextToken(); // [0]
					indice_left = parser.getIntValue();
					parser.nextToken(); // [1]
					indice_right = parser.getIntValue();
					parser.nextToken(); // "]"
				} else if (!newValue(key, parser))
					StreamTools.consumeJsonValue(parser);
			}
		}

		protected Entity(Parcel in) {
			indice_left = in.readInt();
			indice_right = in.readInt();
		}

		protected boolean newValue(String key, JsonParser parser) throws IOException {
			return false;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeInt(indice_left);
			dest.writeInt(indice_right);
		}

	}

	public static class UrlEntity extends Entity {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<UrlEntity> CREATOR = new Parcelable.Creator<UrlEntity>() {
			@Override
			public UrlEntity createFromParcel(Parcel in) {
				return new UrlEntity(in);
			}

			@Override
			public UrlEntity[] newArray(int size) {
				return new UrlEntity[size];
			}
		};

		public boolean _show_expanded;
		public String _expanded_url;
		public String _page_title;

		public String url;
		public String display_url;
		public String expanded_url;

		public UrlEntity(JsonParser parser) throws IOException {
			super(parser);
		}

		protected UrlEntity(Parcel in) {
			super(in);
			url = in.readString();
			display_url = in.readString();
			expanded_url = in.readString();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof UrlEntity && (url.equals(((UrlEntity) o).url));
		}

		@Override
		public int hashCode() {
			return url.hashCode();
		}

		protected boolean newValue(String key, JsonParser parser) throws IOException {
			if (super.newValue(key, parser)) return true;
			switch (key) {
				case "url": url = parser.getText(); break;
				case "display_url": display_url = parser.getText(); break;
				case "expanded_url": expanded_url = parser.getText(); break;
				default:
					return false;
			}
			return true;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeString(url);
			dest.writeString(display_url);
			dest.writeString(expanded_url);
		}

	}

	public static class MediaEntity extends UrlEntity {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<MediaEntity> CREATOR = new Parcelable.Creator<MediaEntity>() {
			@Override
			public MediaEntity createFromParcel(Parcel in) {
				return new MediaEntity(in);
			}

			@Override
			public MediaEntity[] newArray(int size) {
				return new MediaEntity[size];
			}
		};
		public ArrayList<MediaVariants> variants;
		public long id;
		public String media_url;
		public int width;
		public int height;
		public String type;
		public int duration;
		public int aspect[];

		public MediaEntity(JsonParser parser) throws IOException {
			super(parser);
		}

		protected MediaEntity(Parcel in) {
			super(in);
			id = in.readLong();
			media_url = in.readString();
			width = in.readInt();
			height = in.readInt();
			type = in.readString();
			duration = in.readInt();
			if (in.readByte() != 0) {
				aspect = new int[2];
				in.readIntArray(aspect);
			}
			if (in.readByte() != 0) {
				variants = new ArrayList<>();
				in.readList(variants, MediaVariants.class.getClassLoader());
			}
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof MediaEntity && ((MediaEntity) o).media_url.equals(media_url);
		}

		protected boolean newValue(String key, JsonParser parser) throws IOException {
			if (super.newValue(key, parser)) return true;
			switch (key) {
				case "id": id = parser.getLongValue(); break;
				case "media_url": media_url = parser.getText(); break;
				case "type": type = parser.getText(); break;
				case "sizes": {
					while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
						key = parser.getCurrentName();
						parser.nextToken();
						if (parser.getCurrentToken() == JsonToken.VALUE_NULL) continue;
						if (key.equals("large")) {
							while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
								key = parser.getCurrentName();
								parser.nextToken();
								switch (key) {
									case "w": width = parser.getIntValue(); break;
									case "h": height = parser.getIntValue(); break;
									default:
										StreamTools.consumeJsonValue(parser);
								}
							}
						} else StreamTools.consumeJsonValue(parser);
					}
					break;
				}
				case "video_info": {
					while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
						key = parser.getCurrentName();
						parser.nextToken();
						if (parser.getCurrentToken() == JsonToken.VALUE_NULL) continue;
						switch(key){
							case "variants":{
								variants = new ArrayList<>();
								while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_ARRAY) {
									variants.add(new MediaVariants(parser));
								}
								break;
							}
							case "aspect_ratio":{
								aspect = new int[2];
								parser.nextToken(); // [0]
								aspect[0] = parser.getIntValue();
								parser.nextToken(); // [1]
								aspect[1] = parser.getIntValue();
								parser.nextToken(); // "]"
								break;
							}
							case "duration_millis":{
								duration = parser.getIntValue();
								break;
							}
							default: StreamTools.consumeJsonValue(parser);
						}
					}
					break;
				}
				default:
					return false;
			}
			return true;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeLong(id);
			dest.writeString(media_url);
			dest.writeInt(width);
			dest.writeInt(height);
			dest.writeString(type);
			dest.writeInt(duration);
			dest.writeByte((byte) (aspect == null ? 0 : 1));
			if(aspect != null)
				dest.writeIntArray(aspect);
			dest.writeByte((byte) (variants == null ? 0 : 1));
			if(variants != null)
				dest.writeList(variants);
		}

		public static class MediaVariants implements Parcelable {
			@SuppressWarnings("unused")
			public static final Parcelable.Creator<MediaVariants> CREATOR = new Parcelable.Creator<MediaVariants>() {
				@Override
				public MediaVariants createFromParcel(Parcel in) {
					return new MediaVariants(in);
				}

				@Override
				public MediaVariants[] newArray(int size) {
					return new MediaVariants[size];
				}
			};
			public int bitrate;
			public String contentType;
			public String url;

			public MediaVariants(JsonParser parser) throws IOException {
				String key;
				while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
					key = parser.getCurrentName();
					parser.nextToken();
					if (parser.getCurrentToken() == JsonToken.VALUE_NULL) continue;
					switch (key) {
						case "bitrate": bitrate = parser.getIntValue(); break;
						case "url": url = parser.getValueAsString(); break;
						case "content_type": contentType = parser.getValueAsString(); break;
						default:
							StreamTools.consumeJsonValue(parser);
					}
				}
			}

			protected MediaVariants(Parcel in) {
				bitrate = in.readInt();
				contentType = in.readString();
				url = in.readString();
			}

			@Override
			public int describeContents() {
				return 0;
			}

			@Override
			public void writeToParcel(Parcel dest, int flags) {
				dest.writeInt(bitrate);
				dest.writeString(contentType);
				dest.writeString(url);
			}

		}
	}

	public static class MentionsEntity extends Entity {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<MentionsEntity> CREATOR = new Parcelable.Creator<MentionsEntity>() {
			@Override
			public MentionsEntity createFromParcel(Parcel in) {
				return new MentionsEntity(in);
			}

			@Override
			public MentionsEntity[] newArray(int size) {
				return new MentionsEntity[size];
			}
		};
		public Tweeter tweeter;
		public long id;
		public String screen_name;
		public String name;

		public MentionsEntity(JsonParser parser) throws IOException {
			super(parser);
			tweeter = Tweeter.getTweeter(id, screen_name);
			tweeter.name = name;
		}

		protected MentionsEntity(Parcel in) {
			super(in);
			tweeter = (Tweeter) in.readValue(Tweeter.class.getClassLoader());
			id = in.readLong();
			screen_name = in.readString();
			name = in.readString();
		}

		protected boolean newValue(String key, JsonParser parser) throws IOException {
			if (super.newValue(key, parser)) return true;
			switch (key) {
				case "id": id = parser.getLongValue(); break;
				case "screen_name": screen_name = parser.getText(); break;
				case "name": name = parser.getText(); break;
				default:
					return false;
			}
			return true;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeValue(tweeter);
			dest.writeLong(id);
			dest.writeString(screen_name);
			dest.writeString(name);
		}

	}

	public static class HashtagEntity extends Entity {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<HashtagEntity> CREATOR = new Parcelable.Creator<HashtagEntity>() {
			@Override
			public HashtagEntity createFromParcel(Parcel in) {
				return new HashtagEntity(in);
			}

			@Override
			public HashtagEntity[] newArray(int size) {
				return new HashtagEntity[size];
			}
		};
		public String text;

		public HashtagEntity(JsonParser parser) throws IOException {
			super(parser);
		}

		protected HashtagEntity(Parcel in) {
			super(in);
			text = in.readString();
		}

		protected boolean newValue(String key, JsonParser parser) throws IOException {
			if (super.newValue(key, parser)) return true;
			switch (key) {
				case "text": text = parser.getText(); break;
				default:
					return false;
			}
			return true;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeString(text);
		}
	}

	public static class SymbolEntity extends HashtagEntity {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<SymbolEntity> CREATOR = new Parcelable.Creator<SymbolEntity>() {
			@Override
			public SymbolEntity createFromParcel(Parcel in) {
				return new SymbolEntity(in);
			}

			@Override
			public SymbolEntity[] newArray(int size) {
				return new SymbolEntity[size];
			}
		};

		public SymbolEntity(JsonParser parser) throws IOException {
			super(parser);
		}

		protected SymbolEntity(Parcel in) {
			super(in);
		}

	}
}