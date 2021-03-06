/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.rates.file;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.rates.Currency;
import org.opentdc.rates.RateType;
import org.opentdc.rates.RateModel;
import org.opentdc.rates.ServiceProvider;
import org.opentdc.service.ServiceUtil;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.PrettyPrinter;

/**
 * A file-based or transient implementation of the Rates service.
 * @author bruno
 *
 */
public class FileServiceProvider extends AbstractFileServiceProvider<RateModel> implements ServiceProvider {
	
	private static Map<String, RateModel> index = null;
	private static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	/**
	 * Constructor.
	 * @param context the servlet context.
	 * @param prefix the simple class name of the service provider
	 * @throws IOException
	 */
	public FileServiceProvider(
		ServletContext context, 
		String prefix
	) throws IOException {
		super(context, prefix);
		if (index == null) {
			index = new HashMap<String, RateModel>();
			List<RateModel> _rates = importJson();
			for (RateModel _rate : _rates) {
				index.put(_rate.getId(), _rate);
			}
			logger.info(_rates.size() + " Rates imported.");
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.rates.ServiceProvider#list(java.lang.String, java.lang.String, long, long)
	 */
	@Override
	public ArrayList<RateModel> list(
		String query,
		String queryType,
		int position,
		int size
	) {
		ArrayList<RateModel> _rates = new ArrayList<RateModel>(index.values());
		Collections.sort(_rates, RateModel.RateComparator);
		ArrayList<RateModel> _selection = new ArrayList<RateModel>();
		for (int i = 0; i < _rates.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_rates.get(i));
			}			
		}
		logger.info("list(<" + query + ">, <" + queryType + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size() + " rates.");
		return _selection;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.rates.ServiceProvider#create(org.opentdc.rates.RatesModel)
	 */
	@Override
	public RateModel create(
			HttpServletRequest request,
			RateModel rate) 
		throws DuplicateException, ValidationException {
		logger.info("create(" + PrettyPrinter.prettyPrintAsJSON(rate) + ")");
		String _id = rate.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (index.get(_id) != null) {
				// object with same ID exists already
				throw new DuplicateException("rate <" + _id + "> exists already.");
			}
			else { 	// a new ID was set on the client; we do not allow this
				throw new ValidationException("rate <" + _id + 
					"> contains an ID generated on the client. This is not allowed.");
			}
		}
		if (rate.getTitle() == null || rate.getTitle().isEmpty()) {
			throw new ValidationException("rate <" + _id + "> must contain a valid title.");
		}
		if (rate.getRate() < 0) {
			throw new ValidationException("rate <" + _id + ">: negative rates are not allowed.");
		}
		if (rate.getCurrency() == null) {
			rate.setCurrency(Currency.getDefaultCurrency());
		}
		if (rate.getType() == null) {
			rate.setType(RateType.getDefaultRateType());
		}

		rate.setId(_id);
		Date _date = new Date();
		rate.setCreatedAt(_date);
		rate.setCreatedBy(ServiceUtil.getPrincipal(request));
		rate.setModifiedAt(_date);
		rate.setModifiedBy(ServiceUtil.getPrincipal(request));
		index.put(_id, rate);
		logger.info("create(" + PrettyPrinter.prettyPrintAsJSON(rate) + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
		return rate;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.rates.ServiceProvider#read(java.lang.String)
	 */
	@Override
	public RateModel read(
			String id) 
		throws NotFoundException {
		return getRatesModel(id);
	}
	
	public static RateModel getRatesModel(
			String id) 
		throws NotFoundException {
		RateModel _rate = index.get(id);
		if (_rate == null) {
			throw new NotFoundException("no rate with ID <" + id + "> was found.");
		}
		logger.info("getRatesModel(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_rate));
		return _rate;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.rates.ServiceProvider#update(java.lang.String, org.opentdc.rates.RatesModel)
	 */
	@Override
	public RateModel update(
		HttpServletRequest request,
		String id, 
		RateModel rate
	) throws NotFoundException, ValidationException {
		RateModel _rate = index.get(id);
		if(_rate == null) {
			throw new NotFoundException("no rate with ID <" + id
					+ "> was found.");
		} 
		if (rate.getTitle() == null || rate.getTitle().isEmpty()) {
			throw new ValidationException("rate <" + id + ">: title must be defined.");
		}
		if (rate.getRate() < 0) {
			throw new ValidationException("rate <" + id + ">: negative rates are not allowed.");
		}
		if (! _rate.getCreatedAt().equals(rate.getCreatedAt())) {
			logger.warning("rate <" + id + ">: ignoring createdAt value <" + rate.getCreatedAt().toString() + 
					"> because it was set on the client.");
		}
		if (! _rate.getCreatedBy().equalsIgnoreCase(rate.getCreatedBy())) {
			logger.warning("rate <" + id + ">: ignoring createdBy value <" + rate.getCreatedBy() +
					"> because it was set on the client.");
		}
		_rate.setTitle(rate.getTitle());
		_rate.setRate(rate.getRate());
		if (rate.getCurrency() == null) {
			_rate.setCurrency(Currency.getDefaultCurrency());
		} else {
			_rate.setCurrency(rate.getCurrency());		
		}
		if (rate.getType() == null) {
			_rate.setType(RateType.getDefaultRateType());
		} else {
			_rate.setType(rate.getType());
		}
		_rate.setDescription(rate.getDescription());
		_rate.setModifiedAt(new Date());
		_rate.setModifiedBy(ServiceUtil.getPrincipal(request));
		index.put(id, _rate);
		logger.info("update(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_rate));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _rate;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.rates.ServiceProvider#delete(java.lang.String)
	 */
	@Override
	public void delete(
			String id) 
		throws NotFoundException, InternalServerErrorException {
		RateModel _rate = index.get(id);
		if (_rate == null) {
			throw new NotFoundException("rate <" + id
					+ "> was not found.");
		}
		if (index.remove(id) == null) {
			throw new InternalServerErrorException("rate <" + id
					+ "> can not be removed, because it does not exist in the index");
		}
		logger.info("delete(" + id + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
	}
}
