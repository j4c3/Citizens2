package net.citizensnpcs.nms.v1_20_R2.util;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import net.citizensnpcs.Settings.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Target;

public class EntityPathfinder extends PathFinder {
    private final int maxVisitedNodes;
    private final Node[] neighbors = new Node[32];
    private final EntityNodeEvaluator nodeEvaluator;
    private final BinaryHeap openSet;

    public EntityPathfinder(EntityNodeEvaluator var0, int var1) {
        super(var0, var1);
        this.openSet = new BinaryHeap();
        this.nodeEvaluator = var0;
        this.maxVisitedNodes = var1;
    }

    public Path findPath(PathNavigationRegion var0, LivingEntity var1, Set<BlockPos> var2, float var3, int var4,
            float var5) {
        this.openSet.clear();
        this.nodeEvaluator.prepare(var0, var1);
        Node var6 = this.nodeEvaluator.getStart();
        Map<Target, BlockPos> var7 = var2.stream().collect(
                Collectors.toMap(p -> this.nodeEvaluator.getGoal(p.getX(), p.getY(), p.getZ()), Function.identity()));
        Path var8 = findPath(null, var6, var7, var3, var4, var5);
        this.nodeEvaluator.done();
        return var8;
    }

    @Override
    public Path findPath(PathNavigationRegion var0, Mob var1, Set<BlockPos> var2, float var3, int var4, float var5) {
        this.openSet.clear();
        this.nodeEvaluator.prepare(var0, var1);
        Node var6 = this.nodeEvaluator.getStart();
        Map<Target, BlockPos> var7 = var2.stream().collect(
                Collectors.toMap(p -> this.nodeEvaluator.getGoal(p.getX(), p.getY(), p.getZ()), Function.identity()));
        Path var8 = findPath(null, var6, var7, var3, var4, var5);
        this.nodeEvaluator.done();
        return var8;
    }

    private Path findPath(ProfilerFiller var0, Node var1, Map<Target, BlockPos> var2, float var3, int var4,
            float var5) {
        Set<Target> var6 = var2.keySet();
        var1.f = 0.0F;
        var1.h = this.getBestH(var1, var6);
        var1.f = var1.h;
        this.openSet.clear();
        this.openSet.insert(var1);
        Set var7 = ImmutableSet.of();
        int var8 = 0;
        Set<Target> var9 = Sets.newHashSetWithExpectedSize(var6.size());
        int var10 = (int) (this.maxVisitedNodes * var5);

        while (!this.openSet.isEmpty()) {
            ++var8;
            if (var8 >= var10) {
                break;
            }

            Node var11 = this.openSet.pop();
            var11.closed = true;
            Iterator var13i = var6.iterator();

            while (var13i.hasNext()) {
                Target var13 = (Target) var13i.next();
                if (var11.distanceManhattan(var13) <= var4) {
                    var13.setReached();
                    var9.add(var13);
                }
            }

            if (!var9.isEmpty()) {
                break;
            }

            if (!(var11.distanceTo(var1) >= var3)) {
                int var12 = this.nodeEvaluator.getNeighbors(this.neighbors, var11);

                for (int var13 = 0; var13 < var12; ++var13) {
                    Node var14 = this.neighbors[var13];
                    float var15 = this.distance(var11, var14);
                    var14.walkedDistance = var11.walkedDistance + var15;
                    float var16 = var11.f + var15 + var14.costMalus;
                    if (var14.walkedDistance < var3 && (!var14.inOpenSet() || var16 < var14.f)) {
                        var14.cameFrom = var11;
                        var14.f = var16;
                        var14.h = this.getBestH(var14, var6) * 1.5F;
                        if (var14.inOpenSet()) {
                            this.openSet.changeCost(var14, var14.f + var14.h);
                        } else {
                            var14.h += var14.f;
                            this.openSet.insert(var14);
                        }
                    }
                }
            }
        }

        Optional<Path> var11 = !var9.isEmpty() ? var9.stream().map((var1x) -> {
            return this.reconstructPath(var1x.getBestNode(), var2.get(var1x), true);
        }).min(Comparator.comparingInt(Path::getNodeCount)) : getFallbackDestinations(var2, var6).findFirst();
        /*var6.stream().map((var1x) -> {
           return this.reconstructPath(var1x.getBestNode(), (BlockPos)var2.get(var1x), false);
        }).min(Comparator.comparingDouble(Path::getDistToTarget).thenComparingInt(Path::getNodeCount))*/
        if (var11.isEmpty()) {
            return null;
        } else {
            Path var12 = var11.get();
            return var12;
        }
    }

    private float getBestH(Node var0, Set<Target> var1) {
        float var2 = Float.MAX_VALUE;
        for (Target var4 : var1) {
            float var5 = var0.distanceTo(var4);
            var4.updateBest(var5, var0);
            var2 = Math.min(var5, var2);
        }
        return var2;
    }

    public Stream<Path> getFallbackDestinations(Map<Target, BlockPos> var1, Set<Target> var5) {
        if (Setting.DISABLE_MC_NAVIGATION_FALLBACK.asBoolean()) {
            return Stream.empty();
        }
        return var5.stream().map((var1x) -> {
            return this.reconstructPath(var1x.getBestNode(), var1.get(var1x), false);
        }).sorted(Comparator.comparingDouble(Path::getDistToTarget).thenComparingInt(Path::getNodeCount));
    }

    private Path reconstructPath(Node var0, BlockPos var1, boolean var2) {
        List var3 = Lists.newArrayList();
        Node var4 = var0;
        var3.add(0, var0);

        while (var4.cameFrom != null) {
            var4 = var4.cameFrom;
            var3.add(0, var4);
        }

        return new Path(var3, var1, var2);
    }
}
