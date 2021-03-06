package com.merakianalytics.orianna;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.merakianalytics.datapipelines.DataPipeline;
import com.merakianalytics.orianna.datapipeline.DataDragon;
import com.merakianalytics.orianna.datapipeline.GhostObjectSource;
import com.merakianalytics.orianna.datapipeline.ImageDataSource;
import com.merakianalytics.orianna.datapipeline.InMemoryCache;
import com.merakianalytics.orianna.datapipeline.PipelineConfiguration;
import com.merakianalytics.orianna.datapipeline.PipelineConfiguration.PipelineElementConfiguration;
import com.merakianalytics.orianna.datapipeline.PipelineConfiguration.TransformerConfiguration;
import com.merakianalytics.orianna.datapipeline.riotapi.RiotAPI;
import com.merakianalytics.orianna.datapipeline.transformers.dtodata.ChampionMasteryTransformer;
import com.merakianalytics.orianna.datapipeline.transformers.dtodata.ChampionTransformer;
import com.merakianalytics.orianna.datapipeline.transformers.dtodata.LeagueTransformer;
import com.merakianalytics.orianna.datapipeline.transformers.dtodata.MasteriesTransformer;
import com.merakianalytics.orianna.datapipeline.transformers.dtodata.MatchTransformer;
import com.merakianalytics.orianna.datapipeline.transformers.dtodata.RunesTransformer;
import com.merakianalytics.orianna.datapipeline.transformers.dtodata.SpectatorTransformer;
import com.merakianalytics.orianna.datapipeline.transformers.dtodata.StaticDataTransformer;
import com.merakianalytics.orianna.datapipeline.transformers.dtodata.StatusTransformer;
import com.merakianalytics.orianna.datapipeline.transformers.dtodata.SummonerTransformer;
import com.merakianalytics.orianna.types.common.OriannaException;
import com.merakianalytics.orianna.types.common.Platform;
import com.merakianalytics.orianna.types.common.Region;
import com.merakianalytics.orianna.types.core.staticdata.Champion;
import com.merakianalytics.orianna.types.core.staticdata.Item;
import com.merakianalytics.orianna.types.core.staticdata.LanguageStrings;
import com.merakianalytics.orianna.types.core.staticdata.Languages;
import com.merakianalytics.orianna.types.core.staticdata.Maps;
import com.merakianalytics.orianna.types.core.staticdata.Mastery;
import com.merakianalytics.orianna.types.core.staticdata.ProfileIcons;
import com.merakianalytics.orianna.types.core.staticdata.Realm;
import com.merakianalytics.orianna.types.core.staticdata.Rune;
import com.merakianalytics.orianna.types.core.staticdata.SummonerSpell;
import com.merakianalytics.orianna.types.core.staticdata.Versions;
import com.merakianalytics.orianna.types.core.summoner.Summoner;
import com.merakianalytics.orianna.types.core.summoner.Summoners;

public abstract class Orianna {
    public static class Configuration {
        private static final long DEFAULT_CURRENT_VERSION_EXPIRATION = 6;
        private static final TimeUnit DEFAULT_CURRENT_VERSION_EXPIRATION_UNIT = TimeUnit.HOURS;
        private static final String DEFAULT_DEFAULT_LOCALE = null;
        private static final Platform DEFAULT_DEFAULT_PLATFORM = Platform.NORTH_AMERICA;

        private static PipelineConfiguration getDefaultPipeline() {
            final PipelineConfiguration config = new PipelineConfiguration();

            final Set<TransformerConfiguration> transformers = ImmutableSet.of(
                TransformerConfiguration.defaultConfiguration(ChampionMasteryTransformer.class),
                TransformerConfiguration.defaultConfiguration(ChampionTransformer.class),
                TransformerConfiguration.defaultConfiguration(LeagueTransformer.class),
                TransformerConfiguration.defaultConfiguration(MasteriesTransformer.class),
                TransformerConfiguration.defaultConfiguration(MatchTransformer.class),
                TransformerConfiguration.defaultConfiguration(RunesTransformer.class),
                TransformerConfiguration.defaultConfiguration(SpectatorTransformer.class),
                TransformerConfiguration.defaultConfiguration(StaticDataTransformer.class),
                TransformerConfiguration.defaultConfiguration(StatusTransformer.class),
                TransformerConfiguration.defaultConfiguration(SummonerTransformer.class));
            config.setTransformers(transformers);

            final List<PipelineElementConfiguration> elements = ImmutableList.of(
                PipelineElementConfiguration.defaultConfiguration(InMemoryCache.class),
                PipelineElementConfiguration.defaultConfiguration(GhostObjectSource.class),
                PipelineElementConfiguration.defaultConfiguration(DataDragon.class),
                PipelineElementConfiguration.defaultConfiguration(RiotAPI.class),
                PipelineElementConfiguration.defaultConfiguration(ImageDataSource.class));
            config.setElements(elements);

            return config;
        }

        private long currentVersionExpiration = DEFAULT_CURRENT_VERSION_EXPIRATION;
        private TimeUnit currentVersionExpirationUnit = DEFAULT_CURRENT_VERSION_EXPIRATION_UNIT;
        private String defaultLocale = DEFAULT_DEFAULT_LOCALE;
        private Platform defaultPlatform = DEFAULT_DEFAULT_PLATFORM;
        private PipelineConfiguration pipeline = getDefaultPipeline();

        /**
         * @return the currentVersionExpiration
         */
        public long getCurrentVersionExpiration() {
            return currentVersionExpiration;
        }

        /**
         * @return the currentVersionExpirationUnit
         */
        public TimeUnit getCurrentVersionExpirationUnit() {
            return currentVersionExpirationUnit;
        }

        /**
         * @return the defaultLocale
         */
        public String getDefaultLocale() {
            return defaultLocale;
        }

        /**
         * @return the defaultPlatform
         */
        public Platform getDefaultPlatform() {
            return defaultPlatform;
        }

        /**
         * @return the pipeline
         */
        public PipelineConfiguration getPipeline() {
            return pipeline;
        }

        /**
         * @param currentVersionExpiration
         *        the currentVersionExpiration to set
         */
        public void setCurrentVersionExpiration(final long currentVersionExpiration) {
            this.currentVersionExpiration = currentVersionExpiration;
        }

        /**
         * @param currentVersionExpirationUnit
         *        the currentVersionExpirationUnit to set
         */
        public void setCurrentVersionExpirationUnit(final TimeUnit currentVersionExpirationUnit) {
            this.currentVersionExpirationUnit = currentVersionExpirationUnit;
        }

        /**
         * @param defaultLocale
         *        the defaultLocale to set
         */
        public void setDefaultLocale(final String defaultLocale) {
            this.defaultLocale = defaultLocale;
        }

        /**
         * @param defaultPlatform
         *        the defaultPlatform to set
         */
        public void setDefaultPlatform(final Platform defaultPlatform) {
            this.defaultPlatform = defaultPlatform;
        }

        /**
         * @param pipeline
         *        the pipeline to set
         */
        public void setPipeline(final PipelineConfiguration pipeline) {
            this.pipeline = pipeline;
        }
    }

    public static class Settings {
        private final Supplier<String> currentVersion;
        private final String defaultLocale;
        private final Platform defaultPlatform;
        private final DataPipeline pipeline;

        private Settings(final Configuration config) {
            pipeline = PipelineConfiguration.toPipeline(config.getPipeline());
            defaultPlatform = config.getDefaultPlatform();
            defaultLocale = config.getDefaultLocale();
            currentVersion = Suppliers.memoizeWithExpiration(new Supplier<String>() {
                @Override
                public String get() {
                    return pipeline
                        .get(com.merakianalytics.orianna.types.dto.staticdata.Realm.class, ImmutableMap.<String, Object> of("platform", defaultPlatform))
                        .getV();
                }
            }, config.getCurrentVersionExpiration(), config.getCurrentVersionExpirationUnit());
        }

        /**
         * @return the currentVersion
         */
        public String getCurrentVersion() {
            return currentVersion.get();
        }

        /**
         * @return the defaultLocale
         */
        public String getDefaultLocale() {
            return defaultLocale == null ? defaultPlatform.getDefaultLocale() : defaultLocale;
        }

        /**
         * @return the defaultPlatform
         */
        public Platform getDefaultPlatform() {
            return defaultPlatform;
        }

        /**
         * @return the pipeline
         */
        public DataPipeline getPipeline() {
            return pipeline;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Orianna.class);
    private static Settings settings = defaultSettings();

    private static Settings defaultSettings() {
        try {
            return new Settings(getConfiguration(
                Resources.asCharSource(Resources.getResource("com/merakianalytics/orianna/default-orianna-config.json"), Charset.forName("UTF-8"))));
        } catch(final OriannaException e) {
            return new Settings(new Configuration());
        }
    }

    public static Champion.Builder getChampionNamed(final String name) {
        return Champion.named(name);
    }

    public static Champion.Builder getChampionWithId(final int id) {
        return Champion.withId(id);
    }

    public static Champion.Builder getChampionWithKey(final String key) {
        return Champion.withKey(key);
    }

    private static Configuration getConfiguration(final CharSource configJSON) {
        final ObjectMapper mapper = new ObjectMapper().enable(Feature.ALLOW_COMMENTS);
        try {
            return mapper.readValue(configJSON.read(), Configuration.class);
        } catch(final IOException e) {
            LOGGER.error("Failed to load configuration JSON!", e);
            throw new OriannaException("Failed to load configuration JSON!", e);
        }
    }

    public static Item.Builder getItemNamed(final String name) {
        return Item.named(name);
    }

    public static Item.Builder getItemWithId(final int id) {
        return Item.withId(id);
    }

    public static Languages getLanguages() {
        return Languages.get();
    }

    public static LanguageStrings getLanguageStrings() {
        return LanguageStrings.get();
    }

    public static LanguageStrings.Builder getLanguageStringsWithLocale(final String locale) {
        return LanguageStrings.withLocale(locale);
    }

    public static LanguageStrings.Builder getLanguageStringsWithPlatform(final Platform platform) {
        return LanguageStrings.withPlatform(platform);
    }

    public static LanguageStrings.Builder getLanguageStringsWithRegion(final Region region) {
        return LanguageStrings.withRegion(region);
    }

    public static LanguageStrings.Builder getLanguageStringsWithVersion(final String version) {
        return LanguageStrings.withVersion(version);
    }

    public static Languages.Builder getLanguagesWithPlatform(final Platform platform) {
        return Languages.withPlatform(platform);
    }

    public static Languages.Builder getLanguagesWithRegion(final Region region) {
        return Languages.withRegion(region);
    }

    public static Maps getMaps() {
        return Maps.get();
    }

    public static Maps.Builder getMapsWithLocale(final String locale) {
        return Maps.withLocale(locale);
    }

    public static Maps.Builder getMapsWithPlatform(final Platform platform) {
        return Maps.withPlatform(platform);
    }

    public static Maps.Builder getMapsWithRegion(final Region region) {
        return Maps.withRegion(region);
    }

    public static Maps.Builder getMapsWithVersion(final String version) {
        return Maps.withVersion(version);
    }

    public static Mastery.Builder getMasteryNamed(final String name) {
        return Mastery.named(name);
    }

    public static Mastery.Builder getMasteryWithId(final int id) {
        return Mastery.withId(id);
    }

    public static ProfileIcons getProfileIcons() {
        return ProfileIcons.get();
    }

    public static ProfileIcons.Builder getProfileIconsWithLocale(final String locale) {
        return ProfileIcons.withLocale(locale);
    }

    public static ProfileIcons.Builder getProfileIconsWithPlatform(final Platform platform) {
        return ProfileIcons.withPlatform(platform);
    }

    public static ProfileIcons.Builder getProfileIconsWithRegion(final Region region) {
        return ProfileIcons.withRegion(region);
    }

    public static ProfileIcons.Builder getProfileIconsWithVersion(final String version) {
        return ProfileIcons.withVersion(version);
    }

    public static Realm getRealm() {
        return Realm.get();
    }

    public static Realm.Builder getRealmWithPlatform(final Platform platform) {
        return Realm.withPlatform(platform);
    }

    public static Realm.Builder getRealmWithRegion(final Region region) {
        return Realm.withRegion(region);
    }

    public static Rune.Builder getRuneNamed(final String name) {
        return Rune.named(name);
    }

    public static Rune.Builder getRuneWithId(final int id) {
        return Rune.withId(id);
    }

    public static Settings getSettings() {
        return settings;
    }

    public static Summoner.Builder getSummonerNamed(final String name) {
        return Summoner.named(name);
    }

    public static Summoners.Builder getSummonersNamed(final Iterable<String> names) {
        return Summoners.named(names);
    }

    public static Summoners.Builder getSummonersNamed(final String... names) {
        return Summoners.named(names);
    }

    public static SummonerSpell.Builder getSummonerSpellNamed(final String name) {
        return SummonerSpell.named(name);
    }

    public static SummonerSpell.Builder getSummonerSpellWithId(final int id) {
        return SummonerSpell.withId(id);
    }

    public static Summoners.Builder getSummonersWithAccountIds(final Iterable<Long> accountIds) {
        return Summoners.withAccountIds(accountIds);
    }

    public static Summoners.Builder getSummonersWithAccountIds(final long... accountIds) {
        return Summoners.withAccountIds(accountIds);
    }

    public static Summoners.Builder getSummonersWithIds(final Iterable<Long> ids) {
        return Summoners.withIds(ids);
    }

    public static Summoners.Builder getSummonersWithIds(final long... ids) {
        return Summoners.withIds(ids);
    }

    public static Summoner.Builder getSummonerWithAccountId(final long id) {
        return Summoner.withAccountId(id);
    }

    public static Summoner.Builder getSummonerWithId(final long id) {
        return Summoner.withId(id);
    }

    public static Versions getVersions() {
        return Versions.get();
    }

    public static Versions.Builder getVersionsWithPlatform(final Platform platform) {
        return Versions.withPlatform(platform);
    }

    public static Versions.Builder getVersionsWithRegion(final Region region) {
        return Versions.withRegion(region);
    }

    public static void loadConfiguration(final CharSource configJSON) {
        loadConfiguration(getConfiguration(configJSON));
    }

    public static void loadConfiguration(final Configuration config) {
        settings = new Settings(config);
    }

    public static void loadConfiguration(final File configJSON) {
        loadConfiguration(Files.asCharSource(configJSON, Charset.forName("UTF-8")));
    }

    public static void loadConfiguration(final String configJSONResourcePath) {
        loadConfiguration(Resources.asCharSource(Resources.getResource(configJSONResourcePath), Charset.forName("UTF-8")));
    }
}
