package com.spzx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.core.utils.bean.BeanUtils;
import com.spzx.product.api.domain.Product;
import com.spzx.product.api.domain.ProductDetails;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuLockVo;
import com.spzx.product.api.domain.vo.SkuPrice;
import com.spzx.product.api.domain.vo.SkuQuery;
import com.spzx.product.api.domain.vo.SkuStockVo;
import com.spzx.product.domain.SkuStock;
import com.spzx.product.mapper.ProductDetailsMapper;
import com.spzx.product.mapper.ProductMapper;
import com.spzx.product.mapper.ProductSkuMapper;
import com.spzx.product.mapper.SkuStockMapper;
import com.spzx.product.service.IProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品Service业务层处理
 */
@Slf4j
@Service
@Transactional
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    @Autowired
    private ProductDetailsMapper productDetailsMapper;

    @Autowired
    private SkuStockMapper skuStockMapper;

    //@Autowired
    //private StringRedisTemplate stringRedisTemplate; //适合 key和value都是字符串类型

    @Autowired
    private RedisTemplate redisTemplate; //适合值是任意类型

    /**
     * 查询商品列表
     *
     * @param product 商品
     * @return 商品
     */
    @Override
    public List<Product> selectProductList(Product product) {
        return productMapper.selectProductList(product);
    }

    //原子性
    @Override
    public int insertProduct(Product product) {
        //1.保存Product对象到product表
        productMapper.insert(product); //主键回填

        //2.保存List<ProductSku>对象到product_sku表
        List<ProductSku> productSkuList = product.getProductSkuList();
        if (CollectionUtils.isEmpty(productSkuList)) {
            throw new ServiceException("SKU数据为空");
        }
        int size = productSkuList.size();
        for (int i = 0; i < size; i++) {
            ProductSku productSku = productSkuList.get(i);
            productSku.setSkuCode(product.getId() + "_" + i);
            productSku.setSkuName(product.getName() + " " + productSku.getSkuSpec());
            productSku.setProductId(product.getId());
            productSkuMapper.insert(productSku);

            // 修改位图
//            String key = "sku:product:list";
//            redisTemplate.opsForValue().setBit(key, productSku.getId(), true);

            //添加商品库存  //3.保存List<SkuStock>对象到sku_stock表
            SkuStock skuStock = new SkuStock();
            skuStock.setSkuId(productSku.getId());
            skuStock.setTotalNum(productSku.getStockNum());
            skuStock.setLockNum(0);
            skuStock.setAvailableNum(productSku.getStockNum());
            skuStock.setSaleNum(0);
            skuStockMapper.insert(skuStock);
        }

        //4.保存ProductDetails对象到product_details表
        ProductDetails productDetails = new ProductDetails();
        productDetails.setImageUrls(String.join(",", product.getDetailsImageUrlList()));
        productDetails.setProductId(product.getId());
        productDetailsMapper.insert(productDetails);

        return 1;
    }


    @Override
    public Product selectProductById(Long id) {
        //1.根据id查询Product对象
        Product product = productMapper.selectById(id);

        //2.封装扩展字段：查询商品对应多个List<ProductSku>
        //select * from product_sku where product_id =?
        List<ProductSku> productSkuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        List<Long> productSkuIdList = productSkuList.stream().map(productSku -> productSku.getId()).toList();


        // select * from sku_stock where sku_id in (1,2,3,4,5,6)
        List<SkuStock> skuStockList = skuStockMapper.selectList(new LambdaQueryWrapper<SkuStock>().in(SkuStock::getSkuId, productSkuIdList));

        Map<Long, Integer> skuIdToTatalNumMap = skuStockList.stream().collect(Collectors.toMap(SkuStock::getSkuId, SkuStock::getTotalNum));
        productSkuList.forEach(productSku -> {
            //返回ProductSku对象，携带了库存数据；
            productSku.setStockNum(skuIdToTatalNumMap.get(productSku.getId()));
        });

        product.setProductSkuList(productSkuList);

        //3.封装扩展字段：商品详情图片List<String>
        ProductDetails productDetails = productDetailsMapper.selectOne(new LambdaQueryWrapper<ProductDetails>().eq(ProductDetails::getProductId, id));
        String imageUrls = productDetails.getImageUrls();   //url,url,url
        String[] urls = imageUrls.split(",");
        product.setDetailsImageUrlList(Arrays.asList(urls));
        //返回Product对象
        return product;
    }


    @Override
    public int updateProduct(Product product) {
        //1.更新Product
        productMapper.updateById(product);

        //2.更新SKU   List<ProductSku>
        List<ProductSku> productSkuList = product.getProductSkuList();
        if (CollectionUtils.isEmpty(productSkuList)) {
            throw new ServiceException("SKU数据为空");
        }
        productSkuList.forEach(productSku -> {
            productSkuMapper.updateById(productSku);

            //3.更新库存   List<ProductSku> -> 获取扩展字段stockNum
            SkuStock skuStock = skuStockMapper.selectOne(new LambdaQueryWrapper<SkuStock>().eq(SkuStock::getSkuId, productSku.getId()));
            skuStock.setTotalNum(productSku.getStockNum());
            skuStock.setAvailableNum(skuStock.getTotalNum() - skuStock.getLockNum());
            skuStockMapper.updateById(skuStock);
        });

        //4.更新详情ProductDetails
        ProductDetails productDetails = productDetailsMapper
                .selectOne(new LambdaQueryWrapper<ProductDetails>().eq(ProductDetails::getProductId, product.getId()));
        productDetails.setImageUrls(String.join(",", product.getDetailsImageUrlList()));
        productDetailsMapper.updateById(productDetails);

        return 1;
    }


    @Override
    public int deleteProductByIds(Long[] ids) {
        //1.删除Product表数据
        // delete from product where id in (1,2)
        productMapper.deleteBatchIds(Arrays.asList(ids));

        //2.删除ProductSku表数据
        List<ProductSku> productSkuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().in(ProductSku::getProductId, Arrays.asList(ids)));
        List<Long> productSkuIdList = productSkuList.stream().map(ProductSku::getId).toList();
        productSkuMapper.deleteBatchIds(productSkuIdList);

        //3.删除SkuStock表数据
        skuStockMapper.delete(new LambdaQueryWrapper<SkuStock>().in(SkuStock::getSkuId, productSkuIdList));

        //4.删除ProductDetails表数据
        // delete from product_details where product_id in (1,2)
        productDetailsMapper.delete(new LambdaQueryWrapper<ProductDetails>().in(ProductDetails::getProductId, Arrays.asList(ids)));
        return 1;
    }


    @Override
    public void updateAuditStatus(Long id, Integer auditStatus) {
        Product product = new Product();
        product.setId(id);
        if (auditStatus == 1) {
            product.setAuditStatus(1);
            product.setAuditMessage("审批通过");
        } else {
            product.setAuditStatus(-1);
            product.setAuditMessage("审批拒绝");
        }
        productMapper.updateById(product);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Product product = new Product();
        product.setId(id);
        if (status == 1) {
            product.setStatus(1);
        } else {
            product.setStatus(-1);
        }
        productMapper.updateById(product);
    }


    @Override
    public List<ProductSku> getTopSale() {
        return productSkuMapper.getTopSale();
    }


    @Override
    public List<ProductSku> skuList(SkuQuery skuQuery) {
        return productSkuMapper.skuList(skuQuery);
    }


    /**
     * 服务提供者：6个接口来服务于商品详情查询。需要进行优化，提供查询效率。
     * 需要使用redis来提高性能。
     */

    @Override
    public ProductSku getProductSku(Long skuId) {
        // 1. 判断缓存中是否有数据
        String key = "product:sku" + skuId;
        ProductSku productSku = null;
        // 2. 如果缓存中有数据，则直接返回
        if(redisTemplate.hasKey(key)){
            log.info(String.format("线程: id %s, name %s 从缓存中获取数据", Thread.currentThread().getId() ,Thread.currentThread().getName()));
            productSku = (ProductSku)redisTemplate.opsForValue().get(key);
            return productSku;
        }else{
            // 3. 如果缓存中没有数据，则从数据库中查询，并放入缓存中
            // 这里防止多个线程访问数据库，所以要加分布式锁
            String lockKey = "product:sku:lock" + skuId;
            String uuid = UUID.randomUUID().toString().replaceAll("-", "");
            try {
                // 3.1 获得锁
                boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, uuid, 30, TimeUnit.SECONDS);
                // 3.2 如果获得锁成功，则执行业务逻辑，并返回结果
                if(flag){
                    productSku = getProductFromDb(skuId);
                    log.info(String.format("线程: id %d, name %s 从数据库中获取数据", Thread.currentThread().getId() ,Thread.currentThread().getName()));
                    redisTemplate.opsForValue().set(key, productSku, 30 + new Random().nextInt(10), TimeUnit.MINUTES);
                    return productSku;
                }else{
                    // 3.3 如果获得锁失败，则自旋
                    Thread.sleep(200);
                    return getProductSku(skuId);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                // 3.4 释放锁
                // 记得判断以下当前锁是不是自己的
                String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                redisTemplate.execute(
                        new DefaultRedisScript(luaScript, Long.class),
                        Collections.singletonList(lockKey),
                        uuid
                );
            }
        }
    }



    private ProductSku getProductFromDb(Long skuId) {

        ProductSku productSku = productSkuMapper.selectById(skuId);
        return productSku;
    }


    @Override
    public Product getProduct(Long id) {
        return productMapper.selectById(id);
    }


    @Override
    public SkuPrice getSkuPrice(Long skuId) {
        ProductSku productSku = productSkuMapper.selectOne(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getId, skuId).select(ProductSku::getSalePrice, ProductSku::getMarketPrice));
        SkuPrice skuPrice = new SkuPrice();
        BeanUtils.copyProperties(productSku, skuPrice);
        return skuPrice;
    }


    @Override
    public ProductDetails getProductDetails(Long id) {
        return productDetailsMapper.selectOne(new LambdaQueryWrapper<ProductDetails>().eq(ProductDetails::getProductId, id));
    }


    @Override
    public Map<String, Long> getSkuSpecValue(Long id) {
        List<ProductSku> productSkuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id).select(ProductSku::getId, ProductSku::getSkuSpec));
        Map<String, Long> skuSpecValueMap = new HashMap<>();
        productSkuList.forEach(item -> {
            skuSpecValueMap.put(item.getSkuSpec(), item.getId());
        });
        return skuSpecValueMap;
    }


    @Override
    public SkuStockVo getSkuStock(Long skuId) {
        SkuStock skuStock = skuStockMapper.selectOne(new LambdaQueryWrapper<SkuStock>().eq(SkuStock::getSkuId, skuId));
        SkuStockVo skuStockVo = new SkuStockVo();
        BeanUtils.copyProperties(skuStock, skuStockVo);
        return skuStockVo;
    }


    // select * from product_sku where id in (1,2,3)
    // select id,sale_price,market_price from product_sku where id in (1,2,3)
    @Override
    public List<SkuPrice> getSkuPriceList(List<Long> skuIdList) {
        if (CollectionUtils.isEmpty(skuIdList)) {
            return new ArrayList<SkuPrice>();
        }
        List<ProductSku> skuList = productSkuMapper
                .selectList(new LambdaQueryWrapper<ProductSku>().in(ProductSku::getId, skuIdList)
                        .select(ProductSku::getId, ProductSku::getSalePrice, ProductSku::getMarketPrice));
        if (CollectionUtils.isEmpty(skuList)) {
            return new ArrayList<SkuPrice>();
        }
        return skuList.stream().map((sku) -> {
            SkuPrice skuPrice = new SkuPrice();
            skuPrice.setSkuId(sku.getId());
            skuPrice.setSalePrice(sku.getSalePrice());
            skuPrice.setMarketPrice(sku.getMarketPrice());
            return skuPrice;
        }).toList();
    }

    @Override
    @Transactional
    public String checkAndLock(String orderNo, List<SkuLockVo> skuLockVoList) {

        // 1. 去重处理：openfeign 的重试机制可能会导致重复请求，导致重复扣减库存，因此需要做去重处理，保证幂等性
        // 1.1 采用分布式锁来进行去重处理
        // todo : 锁库存业务的锁
        String lockKey = "stock:order:"+ orderNo;
        String dataKey = "stock:lock:" + orderNo; // 记录本次锁定库存的数据，用于解锁或者更新库存
        // 1.2 为了防止死锁，设置锁的过期时间，防止死锁，同时为了防止锁的误删，故值设置为 orderNo

        // 1.3 获得锁
        boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, orderNo, 30, TimeUnit.SECONDS);

        if(!flag){
            // 1.4 获得锁失败,则说明有其他线程以及处理了，直接返回成功
            return "";
        }

        // 获得锁成功，则执行业务逻辑
        // 2. 检查库存
        boolean isEnough = true;
        StringBuilder msg = new StringBuilder();
        for (SkuLockVo vo : skuLockVoList) {
            SkuStock check = skuStockMapper.check(vo.getSkuId(), vo.getSkuNum()); // 检查库存，增加了行锁 for update
            if(check == null){
                isEnough = false;
                msg.append(String.format("商品%d库存不足", vo.getSkuId()));
                vo.setIsHaveStock(false);
            }else{
                vo.setIsHaveStock(true);
            }
        }
        // 3.1 库存足够，锁定库存
        if(isEnough){
            // 实际就是去更新当前商品的库存表：update sku_stock set lock_num = lock_num + 2, available_num = available_num - 2 where id = 21 and del_flag = 0
            for(var vo: skuLockVoList){
                Long skuId = vo.getSkuId();
                Integer skuNum = vo.getSkuNum();
                int affect = skuStockMapper.lock(skuId, skuNum);
                if(affect == 0){
                    // 删除分布式锁
                    redisTemplate.delete(lockKey);
                    throw new ServiceException(String.format("锁定【%d】商品库存失败", skuId));
                }

            }
        }
        // 3.2 库存不足，返回错误信息
        else{
            // 本次事务失败了，我要把锁删掉
            redisTemplate.delete(lockKey);
            return msg.toString() ;
        }

        // 4. 将数据存储到 redis 中，方便解锁或者更新库存
        redisTemplate.opsForValue().set(dataKey, skuLockVoList, 1, TimeUnit.DAYS); // 之后如果解锁了或者更新库存了，记得删除缓存数据
        // last: 释放锁(一定要释放锁）
        redisTemplate.delete(lockKey);
        return "";
    }

    @Override
    @Transactional
    public void unlock(String orderNo) {
        // 1. 使用分布式锁保证幂等性
        String lockKey = "stock:unlock:"+ orderNo;
        String dataKey = "stock:lock:" + orderNo; // 记录本次锁定库存的数据，用于解锁或者更新库存

        // 1.1 获得锁
        boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, "", 1, TimeUnit.HOURS);

        // 1.2 说明之前以及处理过了，直接返回，确保幂等性
        if(!flag){
            return ;
        }

        // 2. 获得锁成功，则执行业务逻辑：释放库存锁
        // 2.1 遍历这些锁定的库存数据，然后执行sql即可：update sku_stock set lock_num = lock_num - ?, available_num = available_num + ? where id = x and del_flag = 0

        List<SkuLockVo> list = (List<SkuLockVo>)redisTemplate.opsForValue().get(dataKey);
        // 没有数据，直接返回
        if(CollectionUtils.isEmpty(list)){
            // 释放锁
            redisTemplate.delete(lockKey);
            log.warn("当前缓存没有锁库存的SkuLockVo信息");
            return ;
        }


        for(var vo :  list){
            // 记录sql是否成功
            int affect = skuStockMapper.unlock(vo.getSkuId(), vo.getSkuNum());
            // sql执行失败，直接回滚
            if(affect == 0){
                // 释放锁
                redisTemplate.delete(lockKey);
                throw new ServiceException("解锁库存失败");
            }
        }

        // 解锁成功后，把缓存数据删了
        redisTemplate.delete(dataKey);

        // 释放锁
        redisTemplate.delete(lockKey);

    }

    @Transactional(rollbackFor = {Exception.class})
    @Override
    public void minus(String orderNo) {
        String key = "sku:minus:" + orderNo;
        String dataKey = "stock:lock:" + orderNo; // 记录本次锁定库存的数据，用于解锁或者更新库存
        //业务去重，防止重复消费
        Boolean isExist = redisTemplate.opsForValue().setIfAbsent(key, orderNo, 1, TimeUnit.HOURS);
        if(!isExist) return;

        // 获取锁定库存的缓存信息
        List<SkuLockVo> skuLockVoList = (List<SkuLockVo>)this.redisTemplate.opsForValue().get(dataKey);
        if (CollectionUtils.isEmpty(skuLockVoList)){
            return ;
        }

        // 减库存
        skuLockVoList.forEach(skuLockVo -> {
            int row = skuStockMapper.minus(skuLockVo.getSkuId(), skuLockVo.getSkuNum());
            if(row == 0) {
                //解除去重
                this.redisTemplate.delete(key);
                throw new ServiceException("减出库失败");
            }
        });

        // 解锁库存之后，删除锁定库存的缓存。以防止重复解锁库存
        this.redisTemplate.delete(dataKey);
        this.redisTemplate.delete(key);
    }

}